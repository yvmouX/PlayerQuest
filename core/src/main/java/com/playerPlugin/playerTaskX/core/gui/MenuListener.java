package com.playerPlugin.playerTaskX.core.gui;

import com.playerPlugin.playerTaskX.PlayerTaskX;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.util.Objects;
import java.util.logging.Level;

/**
 * 菜单事件监听器：把容器上的点击翻译成菜单项的 action。
 * 判归属只能看视图的上层容器（玩家点自己背包时 clickedInventory 是背包）；必须先无条件取消事件再执行动作，否则动作抛异常就会漏掉取消；派发期间用 {@link #dispatching} 防重入。
 */
public final class MenuListener implements Listener {

    /** 插件主类：取 messages() 与写日志（动作抛异常时必须留下可排查的痕迹）。 */
    private final PlayerTaskX plugin;

    /** 是否正在派发动作，用于防重入。服务端事件在同一线程串行处理，普通布尔量足够。 */
    private boolean dispatching;

    public MenuListener(PlayerTaskX plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** 点击派发：显式 {@code ignoreCancelled = false}——别的插件取消了也要取消事件本身（否则物品能被拖走），但不再执行动作。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        Menu menu = menuOf(event.getView());
        if (menu == null) {
            return;
        }
        // 先记下「是否已被别的插件取消」，再补上我们自己的取消：
        // 顺序反了就无从区分，会被自己的 setCancelled 掩盖掉。
        boolean cancelledByOther = event.isCancelled();
        event.setCancelled(true);

        // 别的插件已经否掉了这次点击 → 只保住菜单完整，不执行动作；正在派发中 → 防重入
        if (cancelledByOther || dispatching) {
            return;
        }

        int rawSlot = event.getRawSlot();
        // 落在玩家背包区的点击（rawSlot >= 菜单大小）只取消，不派发
        if (rawSlot < 0 || rawSlot >= menu.size()) {
            return;
        }
        MenuItem item = menu.itemAt(rawSlot);
        if (item == null) {
            return;
        }

        dispatching = true;
        try {
            // 统一用菜单的 viewer 而不是 event.getWhoClicked()：菜单是为某一个玩家构建的视图，
            // 标题、进度、按钮语义都绑定在该玩家身上，动作里的 player 必须与之一致。
            item.action().accept(new MenuItem.ClickContext(menu.viewer(), event.getClick()));
        } catch (RuntimeException e) {
            // 单个动作失败不能连累后续交互，但必须留下日志：否则玩家只会看到「点了没反应」
            plugin.getLogger().log(Level.SEVERE,
                    "菜单动作执行失败: " + menu.getClass().getSimpleName() + " slot=" + rawSlot, e);
        } finally {
            dispatching = false;
        }
    }

    /**
     * 拖拽一律取消。
     * <p>
     * 一次拖拽可能同时覆盖菜单槽与背包槽，逐槽判断既复杂又没有意义
     * （菜单里没有一个槽位是允许放东西的），整体拒绝最简单也最安全。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (menuOf(event.getView()) == null) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * 从视图解析出本插件的菜单，不是我们的容器一律返回 {@code null}。
     * <p>
     * 归属判定只看 {@code InventoryHolder} 的类型，不看标题：标题会随语言文件变化、
     * 可能与其它插件的容器重名，属于展示层数据，不能当身份用。
     */
    private static Menu menuOf(InventoryView view) {
        if (view == null) {
            return null;
        }
        Inventory top = view.getTopInventory();
        if (top == null || !(top.getHolder() instanceof Menu.MenuHolder holder)) {
            return null;
        }
        return holder.menu();
    }
}
