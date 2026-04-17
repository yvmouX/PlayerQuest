package com.playerPlugin.playerTaskX.command;

import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class TaskCommand implements CommandExecutor, TabCompleter {
    private final TaskManager taskManager;

    public TaskCommand(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> listTasks(player);
            case "accept" -> acceptTask(player, args);
            case "progress" -> showProgress(player);
            case "claim" -> claimReward(player, args);
            case "abandon" -> abandonTask(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("=== Task Commands ===");
        player.sendMessage("/task list - View available tasks");
        player.sendMessage("/task accept <id> - Accept a task");
        player.sendMessage("/task progress - View your progress");
        player.sendMessage("/task claim <id> - Claim reward");
        player.sendMessage("/task abandon <id> - Abandon task");
    }

    private void listTasks(Player player) {
        List<TaskDefinition> available = taskManager.getAvailableTasks(player);
        if (available.isEmpty()) {
            player.sendMessage("No tasks available");
            return;
        }
        player.sendMessage("=== Available Tasks ===");
        for (TaskDefinition task : available) {
            player.sendMessage(task.getId() + ": " + task.getName() + " - " + task.getDescription());
        }
    }

    private void acceptTask(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("Usage: /task accept <id>");
            return;
        }
        String taskId = args[1];
        if (taskManager.acceptTask(player, taskId)) {
            player.sendMessage("Task accepted!");
        } else {
            player.sendMessage("Failed to accept task. Check if you meet requirements.");
        }
    }

    private void showProgress(Player player) {
        player.sendMessage("=== Your Progress ===");
    }

    private void claimReward(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("Usage: /task claim <id>");
            return;
        }
        String taskId = args[1];
        if (taskManager.claimReward(player, taskId)) {
            player.sendMessage("Reward claimed!");
        } else {
            player.sendMessage("Failed to claim reward. Task may not be completed.");
        }
    }

    private void abandonTask(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("Usage: /task abandon <id>");
            return;
        }
        String taskId = args[1];
        if (taskManager.abandonTask(player, taskId)) {
            player.sendMessage("Task abandoned.");
        } else {
            player.sendMessage("Failed to abandon task.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}
