package com.playerPlugin.playerTaskX.core.gui.editor;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 把管理员在聊天栏敲的那句话喂给 {@link EditorInput}。
 * 用 Spigot 的 {@link AsyncPlayerChatEvent}：Paper 的 AsyncChatEvent 不在 spigot-api 里，会在 Spigot 上加载失败。
 */
public final class ChatInputListener implements Listener {

    /**
     * LOWEST 优先且不忽略已取消事件：等待输入期间那句话必须被拦下，否则会漏进公屏。
     * 先取消再取值——反过来的话，取值期间别的监听器可能已经放行，消息照样漏出去。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!EditorInput.awaiting(player)) {
            return;
        }
        event.setCancelled(true);
        // 事件是异步的，回调切主线程由 EditorInput 内部负责
        EditorInput.finish(player, event.getMessage());
    }

    /** 退服的玩家不再等输入：留着状态会让下一句聊天被吞掉。 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        EditorInput.forget(event.getPlayer());
    }
}
