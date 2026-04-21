package com.playerPlugin.playerTaskX.command;

import cn.yvmou.ylib.command.annotation.Arg;
import cn.yvmou.ylib.command.annotation.Command;
import cn.yvmou.ylib.command.annotation.SubCommand;
import cn.yvmou.ylib.command.context.CommandContext;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;

@Command(name = "playertaskx", aliases = "ptx", description = "PlayerTaskX player commands")
public class TaskCommand {

    private final TaskManager taskManager;

    public TaskCommand(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    /** 显示玩家命令帮助信息 */
    @SubCommand("")
    public void help(CommandSender sender) {
        sender.sendMessage("=== PlayerTaskX Commands ===");
        sender.sendMessage("/task list - View available tasks");
        sender.sendMessage("/task accept <id> - Accept a task");
        sender.sendMessage("/task progress - View your progress");
        sender.sendMessage("/task claim <id> - Claim reward");
        sender.sendMessage("/task abandon <id> - Abandon task");
    }

    /** 列出玩家可接取的任务 */
    @SubCommand("list")
    public void listTasks(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players");
            return;
        }
        List<TaskDefinition> available = taskManager.getAvailableTasks(player);
        if (available.isEmpty()) {
            sender.sendMessage("No tasks available");
            return;
        }
        sender.sendMessage("=== Available Tasks ===");
        for (TaskDefinition task : available) {
            log.to(sender).msg("&e{} {}", task.getName(), task.getId());
        }
    }

    /** 玩家接取指定任务 */
    @SubCommand("accept")
    public void acceptTask(CommandSender sender, 
                           @Arg(value = "id", suggestion = "suggestAvailableTasks") String taskId) {
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

    /** 显示玩家任务进度 */
    @SubCommand("progress")
    public void showProgress(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players");
            return;
        }
        player.sendMessage("=== Your Progress ===");
    }

    /** 玩家领取任务奖励 */
    @SubCommand("claim")
    public void claimReward(CommandSender sender, 
                            @Arg(value = "id", suggestion = "suggestClaimableTasks") String taskId) {
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

    /** 玩家放弃任务 */
    @SubCommand("abandon")
    public void abandonTask(CommandSender sender, 
                           @Arg(value = "id", suggestion = "suggestAbandonableTasks") String taskId) {
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

    /** 补全：获取玩家可接取的任务ID列表 */
    public List<String> suggestAvailableTasks(CommandSender sender, 
                                              CommandContext context, 
                                              String current) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        return taskManager.getAvailableTasks(player).stream()
            .map(TaskDefinition::getId)
            .filter(id -> id.toLowerCase().startsWith(current.toLowerCase()))
            .collect(Collectors.toList());
    }

    /** 补全：获取玩家已完成可领取奖励的任务ID列表 */
    public List<String> suggestClaimableTasks(CommandSender sender, 
                                               CommandContext context, 
                                               String current) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        UUID playerId = player.getUniqueId();
        return taskManager.getPlayerProgressCache().getOrDefault(playerId, java.util.Map.of()).values().stream()
            .filter(TaskProgress::isCompleted)
            .map(TaskProgress::getTaskId)
            .filter(id -> id.toLowerCase().startsWith(current.toLowerCase()))
            .collect(Collectors.toList());
    }

    /** 补全：获取玩家正在进行的任务ID列表（可放弃的） */
    public List<String> suggestAbandonableTasks(CommandSender sender, 
                                                CommandContext context, 
                                                String current) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        UUID playerId = player.getUniqueId();
        return taskManager.getPlayerProgressCache().getOrDefault(playerId, java.util.Map.of()).values().stream()
            .map(TaskProgress::getTaskId)
            .filter(id -> id.toLowerCase().startsWith(current.toLowerCase()))
            .collect(Collectors.toList());
    }
}
