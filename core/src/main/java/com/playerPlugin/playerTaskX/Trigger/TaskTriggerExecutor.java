package com.playerPlugin.playerTaskX.Trigger;

import com.playerPlugin.playerTaskX.model.TaskDefinition;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class TaskTriggerExecutor {

    /**
     * 执行触发器命令列表
     */
    public static void execute(Player player, List<String> commands, TaskDefinition taskDefinition) {
        if (commands == null || commands.isEmpty()) return;

        for (String command : commands) {
            if (command == null || command.isEmpty()) continue;
            
            // 替换占位符
            command = command.replace("{player}", player.getName());
            command = command.replace("{this}", taskDefinition.getName());
            
            executeCommand(player, command);
        }
    }

    /**
     * 执行单个命令
     */
    private static void executeCommand(Player player, String command) {
        String[] parts = command.split(":", 2);
        if (parts.length != 2) return;

        String type = parts[0].toLowerCase(Locale.ENGLISH);
        String args = parts[1].trim();

        switch (type) {
            // 消息相关
            case "message":
                TaskTriggerMessage.handleMessage(player, args);
                break;
            case "bc":
                TaskTriggerMessage.handleBroadcast(player, args);
                break;
            // 经济相关
            case "money":
                TaskTriggerEco.handleMoney(player, args);
                break;
            case "point":
                TaskTriggerEco.handlePoint(player, args);
                break;
            case "xp":
                TaskTriggerEco.handleXP(player, args);
                break;
            case "level":
                TaskTriggerEco.handleLevel(player, args);
                break;
            // 命令相关
            case "command":
                TaskTriggerCommand.handleCommand(player, args);
                break;
            case "admin":
                TaskTriggerCommand.handleAdmin(player, args);
                break;
            case "console":
                TaskTriggerCommand.handleConsole(player, args);
                break;
            // 特殊命令
            case "kill":
                TaskTriggerSpecial.handleKill(player, args);
                break;
            case "potion":
                TaskTriggerSpecial.handlePotion(player, args);
                break;
            case "item":
                TaskTriggerSpecial.handleItem(player, args);
                break;
            default:
                break;
        }
    }
}
