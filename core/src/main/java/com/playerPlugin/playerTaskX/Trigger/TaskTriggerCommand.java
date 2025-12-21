package com.playerPlugin.playerTaskX.Trigger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TaskTriggerCommand {
    /**
     * handle 玩家命令
     *
     * @param player  选手
     * @param command 命令
     */
    protected static void handleCommand(Player player, String command) {
        command = command.replace("{player}", player.getName());
        player.performCommand(command);
    }

    /**
     * 处理管理员命令
     *
     * @param player  选手
     * @param command 命令
     */
    protected static void handleAdmin(Player player, String command) {
        command = command.replace("{player}", player.getName());
        // 临时设置 OP 执行命令
        boolean wasOp = player.isOp();
        try {
            if (!wasOp) player.setOp(true);
            player.performCommand(command);
        } finally {
            if (!wasOp) player.setOp(false);
        }
    }

    /**
     * 处理控制台命令
     *
     * @param player  选手
     * @param command 命令
     */
    protected static void handleConsole(Player player, String command) {
        command = command.replace("{player}", player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
