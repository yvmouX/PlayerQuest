package com.playerPlugin.playerTaskX.command;

import cn.yvmou.ylib.command.annotation.Arg;
import cn.yvmou.ylib.command.annotation.Command;
import cn.yvmou.ylib.command.annotation.SubCommand;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@Command(name = "playertaskx", description = "PlayerTaskX player commands")
public class TaskCommand {

    private final TaskManager taskManager;

    public TaskCommand(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @SubCommand("")
    public void help(CommandSender sender) {
        sender.sendMessage("=== PlayerTaskX Commands ===");
        sender.sendMessage("/task list - View available tasks");
        sender.sendMessage("/task accept <id> - Accept a task");
        sender.sendMessage("/task progress - View your progress");
        sender.sendMessage("/task claim <id> - Claim reward");
        sender.sendMessage("/task abandon <id> - Abandon task");
    }

    @SubCommand("list")
    public void listTasks(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players");
            return;
        }
        List<?> available = taskManager.getAvailableTasks(player);
        if (available.isEmpty()) {
            sender.sendMessage("No tasks available");
            return;
        }
        sender.sendMessage("=== Available Tasks ===");
        for (Object task : available) {
            sender.sendMessage(task.toString());
        }
    }

    @SubCommand("accept")
    public void acceptTask(CommandSender sender, @Arg("id") String taskId) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players");
            return;
        }
        if (taskManager.acceptTask(player, taskId)) {
            player.sendMessage("Task accepted!");
        } else {
            player.sendMessage("Failed to accept task. Check if you meet requirements.");
        }
    }

    @SubCommand("progress")
    public void showProgress(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players");
            return;
        }
        player.sendMessage("=== Your Progress ===");
    }

    @SubCommand("claim")
    public void claimReward(CommandSender sender, @Arg("id") String taskId) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players");
            return;
        }
        if (taskManager.claimReward(player, taskId)) {
            player.sendMessage("Reward claimed!");
        } else {
            player.sendMessage("Failed to claim reward. Task may not be completed.");
        }
    }

    @SubCommand("abandon")
    public void abandonTask(CommandSender sender, @Arg("id") String taskId) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players");
            return;
        }
        if (taskManager.abandonTask(player, taskId)) {
            player.sendMessage("Task abandoned.");
        } else {
            player.sendMessage("Failed to abandon task.");
        }
    }
}
