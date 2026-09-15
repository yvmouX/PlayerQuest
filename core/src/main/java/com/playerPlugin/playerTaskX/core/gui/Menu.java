package com.playerPlugin.playerTaskX.core.gui;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 箱子菜单框架：只负责「建容器、登记槽位、刷新重建、标记归属」四件事，
 * 具体摆什么物品交给子类在 {@link #build()} 里决定。
 *
 * <h2>为什么用 {@link MenuHolder} 判归属，而不是比较标题</h2>
 * 标题是展示文本：它会随语言文件变、两个菜单可能同名、也可能和别的插件撞上，
 * 更别说 {@code Inventory#getTitle()} 在部分实现里本就不稳定。
 * {@code InventoryHolder} 是对象身份，因此监听器只认
 * {@code getView().getTopInventory().getHolder() instanceof MenuHolder}。
 *
 * <h2>为什么 build() 由子类在构造末尾触发，而不是在基类构造里调用</h2>
 * 基类构造执行时子类字段尚未赋值，此时回调 {@link #build()} 必然读到 {@code null}
 * （{@code QuestDetailMenu} 的任务定义与玩家进度就是这类字段），是典型的
 * 「构造期调用可覆写方法」陷阱。因此约定：<b>子类在构造最后一行调用 {@link #refresh()}</b>。
 * 漏写不会打开空界面，但会少一次填充——没有兜底可言，只能靠这条约定。
 *
 * <h2>刷新与重入</h2>
 * 点击动作里常常要「操作完立刻刷新界面」，而刷新会清空并重建 inventory。
 * {@link #refresh()} 自带重入保护：若 {@link #build()} 内部再触发刷新会直接返回，
 * 避免无限递归；监听器一侧也有独立的重入保护（见 {@link MenuListener}）。
 */
public abstract class Menu {

    /** 打开界面的玩家（本菜单只为这一个玩家构建）。 */
    private final Player viewer;

    /** 语言服务（由调用方传入，菜单不自己去 {@code getInstance()} 取，便于测试与替换）。 */
    private final MessageService messages;

    /** 容器大小，已规范为 9 的倍数。 */
    private final int size;

    /** 槽位 → 菜单项。以它而不是 inventory 内容为准：取出来的 ItemStack 已被 Bukkit 复制过。 */
    private final Map<Integer, MenuItem> items = new HashMap<>();

    /** 归属标记，必须在 inventory 之前初始化（createInventory 需要它）。 */
    private final MenuHolder holder;

    private final Inventory inventory;

    /** {@link #refresh()} 的重入保护。 */
    private boolean rebuilding;

    /**
     * @param viewer    打开界面的玩家
     * @param messages  语言服务
     * @param size      容器大小（9~54，非 9 的倍数会被向下取整）
     * @param titleKey  标题的语言键
     * @param titleArgs 标题占位符参数（{0}、{1}…）
     */
    protected Menu(Player viewer, MessageService messages, int size, String titleKey, Object... titleArgs) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.size = normalizeSize(size);
        this.holder = new MenuHolder();
        // holder 先于 inventory 赋值：createInventory 需要 holder，而 holder.getInventory() 读的正是下面这个字段
        this.inventory = Bukkit.createInventory(holder, this.size, text(titleKey, titleArgs));
    }

    // ---------- 基本访问 ----------

    /** 打开界面的玩家。 */
    public final Player viewer() {
        return viewer;
    }

    /** 语言服务。 */
    protected final MessageService messages() {
        return messages;
    }

    /** 容器大小（槽位数）。 */
    public final int size() {
        return size;
    }

    /** 打开界面。内容由子类构造末尾的 {@link #refresh()} 填好。 */
    public final void open() {
        viewer.openInventory(inventory);
    }

    // ---------- 子类接口 ----------

    /**
     * 填充物品。由 {@link #refresh()} 调用，可能在任意时刻被反复调用，
     * 因此实现里要按当前真实数据重新算一遍，不能依赖上一次的结果。
     */
    protected abstract void build();

    /**
     * 在指定槽位放一个菜单项。
     * <p>
     * 越界或 {@code null} 会被静默忽略：界面数据来自配置与数据库，
     * 不该因为一条脏数据（目标数超出预留区域）就抛异常把整个界面打断。
     */
    protected final void set(int slot, MenuItem item) {
        if (item == null || slot < 0 || slot >= size) {
            return;
        }
        items.put(slot, item);
        inventory.setItem(slot, item.icon());
    }

    /** 用同一个菜单项填满尚未占用的空位（背景板、禁用态按钮的铺底）。 */
    protected final void fill(MenuItem item) {
        for (int slot = 0; slot < size; slot++) {
            if (!items.containsKey(slot)) {
                set(slot, item);
            }
        }
    }

    /**
     * 清空后重跑 {@link #build()}，用于操作后即时刷新界面。
     * <p>
     * 正在重建时再次调用会直接返回：动作里刷新、{@code build()} 里又刷新这类写法
     * 若没有这道闸门就是无限递归。
     */
    public final void refresh() {
        if (rebuilding) {
            return;
        }
        rebuilding = true;
        try {
            items.clear();
            inventory.clear();
            build();
        } finally {
            rebuilding = false;
        }
    }

    // ---------- 供监听器使用（包内可见，不对外暴露） ----------

    /** 取槽位上的菜单项，没有则返回 {@code null}。 */
    final MenuItem itemAt(int slot) {
        return items.get(slot);
    }

    // ---------- 文本 ----------

    /**
     * 渲染语言键文本（占位符按 {0}、{1}… 顺序替换）。
     * <p>
     * 用 {@code raw(viewer, key, args)} 而不是 {@code raw(key, args)}：
     * 前者在配置开启 {@code use-client-locale} 时按玩家客户端语言解析，
     * 与 {@code messages.send(...)} 的聊天提示保持同一语言，否则同一个玩家
     * 会在聊天里看到一种语言、在界面标题里看到另一种。
     */
    protected final String text(String key, Object... args) {
        return Texts.render(messages.raw(viewer, key, args));
    }

    /**
     * 规范化容器大小。
     * <p>
     * {@code Bukkit.createInventory} 对非 9 倍数的大小会直接抛 {@code IllegalArgumentException}，
     * 与其让调用方在构造界面时炸掉，不如在这里夹紧到合法区间。
     */
    private static int normalizeSize(int size) {
        if (size < 9) {
            return 9;
        }
        if (size > 54) {
            return 54;
        }
        return size - (size % 9);
    }

    /**
     * 归属标记：把「这个容器属于哪个菜单」记在 {@code InventoryHolder} 上。
     * <p>
     * 做成非静态内部类，是为了让 {@link #getInventory()} 直接返回外层字段，
     * 不必再回填一次引用；该字段在构造中赋值，而 Bukkit 不会在
     * {@code createInventory} 期间回调持有者，因此不存在读到 null 的窗口。
     */
    public final class MenuHolder implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        /** 容器归属的菜单；监听器据此派发点击。 */
        Menu menu() {
            return Menu.this;
        }
    }
}
