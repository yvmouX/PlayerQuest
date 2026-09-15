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
 * 箱子菜单框架：建容器、登记槽位、刷新重建、标记归属，摆什么物品交给子类在 {@link #build()} 里决定。
 * 子类必须在构造最后一行调用 {@link #refresh()}——基类构造期回调会读到未赋值的子类字段；归属判定用 {@link MenuHolder} 对象身份而非标题。
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

    /** 由 {@link #layout(String...)} 声明的布局；没声明时为 {@code null}（那时只能按数字下标摆位）。 */
    private SlotLayout layout;

    /** 容器大小会被夹到 9~54 并按 9 向下取整，标题走语言键与占位符参数。 */
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

    /**
     * 声明界面布局：在 {@link #build()} 开头写一张文本图（见 {@link SlotLayout}），之后就能用
     * {@link #set(String, MenuItem)} 按名字摆位。
     * <p>
     * 翻页列表那种整片区域仍用数字下标——45 个格子写成文本图没有可读性，布局表只管锚点与按钮区。
     */
    protected final void layout(String... rows) {
        this.layout = SlotLayout.parse(size, rows);
    }

    /** 布局里的槽位名 → 下标；没声明布局、或名字不在表里都抛（名字写错必须当场炸）。 */
    protected final int slot(String name) {
        if (layout == null) {
            throw new IllegalStateException("还没声明布局就按名字取槽位「" + name + "」：先在 build() 里调用 layout(...)");
        }
        return layout.slot(name);
    }

    /** 按布局里的槽位名摆一个菜单项。 */
    protected final void set(String name, MenuItem item) {
        set(slot(name), item);
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

    /** 渲染语言键文本（占位符按 {0}、{1}… 顺序替换）；用 {@code raw(viewer, ...)} 按玩家客户端语言解析，与聊天提示保持同一语言。 */
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

    /** 归属标记：做成非静态内部类，{@link #getInventory()} 直接返回外层字段，因此不存在读到 {@code null} 的窗口。 */
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
