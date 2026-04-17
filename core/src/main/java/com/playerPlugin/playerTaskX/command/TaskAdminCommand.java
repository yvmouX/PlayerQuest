package com.playerPlugin.playerTaskX.command;

import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class TaskAdminCommand implements CommandExecutor {
    private final TaskManager taskManager;

    public TaskAdminCommand(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("You don't have permission to use this command");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> reloadTasks(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("=== Task Admin Commands ===");
        sender.sendMessage("/taskadmin reload - Reload task configurations");
    }

    private void reloadTasks(CommandSender sender) {
        taskManager.loadTasks();
        sender.sendMessage("Tasks reloaded!");
    }
}
