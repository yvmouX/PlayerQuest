package com.playerPlugin.playerTaskX.domain.PlayerTask.Trigger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TaskTriggerMessage {
    /**
     * 处理消息
     *
     * @param player  选手
     * @param message 消息
     */
    protected static void handleMessage(Player player, String message) {
        String newMessage = message.replace("{player}", player.getName());
        player.sendMessage(newMessage.replace("&", "§"));
    }

    /**
     * 处理广播
     *
     * @param player  选手
     * @param message 消息
     */
    protected static void handleBroadcast(Player player, String message) {
        String newMessage = message.replace("{player}", player.getName());
        Bukkit.broadcastMessage(newMessage.replace("&", "§"));
    }
}
