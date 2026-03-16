package com.playerPlugin.playerTaskX.commands;

import cn.yvmou.ylib.api.command.CommandManager;
import cn.yvmou.ylib.api.config.ConfigurationManager;
import cn.yvmou.ylib.api.logger.Logger;
import cn.yvmou.ylib.command.annotation.Arg;
import cn.yvmou.ylib.command.annotation.Command;
import cn.yvmou.ylib.command.annotation.Optional;
import cn.yvmou.ylib.command.annotation.SubCommand;
import com.playerPlugin.playerTaskX.TaskAPI;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskType;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.configuration.EditorConfiguration;
import com.playerPlugin.playerTaskX.configuration.StorgeConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

@Command(name = "playertaskxadmin", aliases = {"ptxadmin", "ptxa"}, description = "PlayerTaskX Admin Command", permission = "playertaskx.admin")
public class AdminCommand {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(AdminCommand.class);
    private final Logger logger;
    private final TaskAPI api;
    private final TaskCache cache;
    private final ConfigurationManager configurationManager;
    private final CommandManager commandManager;

    public AdminCommand(Logger logger, TaskAPI api, TaskCache cache, ConfigurationManager configurationManager, CommandManager commandManager) {
        this.logger = logger;
        this.api = api;
        this.cache = cache;
        this.configurationManager = configurationManager;
        this.commandManager = commandManager;
    }

    @SubCommand("")
    public void noArgs(CommandSender sender) {
        sendHelp(sender);
    }

    @SubCommand(value = "help", permission = "playertaskx.admin.help")
    public void help(CommandSender sender) {
        sendHelp(sender);
    }

    // 1. Task Group
    @SubCommand("task")
    public class TaskGroup {
        
        @SubCommand(value = "create", permission = "playertaskx.admin.task_create")
        public void create(CommandSender sender,
                           @Arg("type") PTXTaskType taskType,
                           @Arg("id") String taskID,
                           @Optional @Arg("name") String taskName,
                           @Optional @Arg("description") String description) {
            if (!PTXTaskType.isValid(taskType)) {
                logger.to(sender).info("Invalid taskType");
                return;
            }

            if (isInvalid(sender, taskID)) return;

            if (taskName == null || taskName.isEmpty()) taskName = sender.getName() + "创建的任务";
            if (description == null || description.isEmpty()) description = "描述";

            TaskDefinition takDef = new TaskDefinition(taskID, taskType, taskName, description, List.of());

            api.createTask(takDef);
            logger.to(sender).info("Created successfully! id: {}, Type: {}, Name: {}, Description: {}", taskID, taskType, taskName, description);
        }

        @SubCommand(value = "delete", permission = "playertaskx.admin.task_delete")
        public void delete(CommandSender sender, @Arg(value = "id", suggestion = "getTaskIds") String taskID) {
            if (isInvalid(sender, taskID)) return;

            api.deleteTask(taskID);
            logger.to(sender).info("Deleted successfully! id: {}", taskID);
        }

        @SubCommand(value = "list", permission = "playertaskx.admin.task_list", description = "list command")
        public void list(CommandSender sender) {
            sender.sendMessage("§6=== 所有可用任务 ===");
            Objects.requireNonNull(cache.getTaskDefList()).forEach(task -> {
                sender.sendMessage(String.format("§e%s §7- §f%s", task.getId(), task.getName()));
            });
        }

        public List<String> getTaskIds(CommandSender sender, cn.yvmou.ylib.command.context.CommandContext context, String input) {
            return cache.getTaskDefList().stream()
                    .map(com.playerPlugin.playerTaskX.api.model.TaskDefinition::getId)
                    .toList();
        }
    }

    // 2. Progress Group
    @SubCommand("progress")
    public class ProgressGroup {
        
        @SubCommand(value = "start", permission = "playertaskx.admin.progress.start")
        public void start(CommandSender sender, @Arg("player") Player player, @Arg(value = "taskID", suggestion = "getTaskIds") String taskID) {
            if (isInvalid(sender, player, taskID)) return;
            
            if (api.createProgress(player, taskID)) {
                logger.to(sender).info("Started task {} for player {}", taskID, player.getName());
            } else {
                logger.to(sender).error("Failed to start task {} for player {}", taskID, player.getName());
            }
        }

        @SubCommand(value = "delete", permission = "playertaskx.admin.progress.delete")
        public void delete(CommandSender sender, @Arg("player") Player player, @Arg(value = "taskID", suggestion = "getTaskIds") String taskID) {
            if (isInvalid(sender, player, taskID)) return;

            if (api.deleteProgress(player, taskID)) {
                logger.to(sender).info("Deleted progress of task {} for player {}", taskID, player.getName());
            } else {
                logger.to(sender).error("Failed to delete progress of task {} for player {}", taskID, player.getName());
            }
        }

        @SubCommand(value = "update", permission = "playertaskx.admin.progress.update")
        public void update(CommandSender sender, @Arg("player") Player player, @Arg(value = "taskID", suggestion = "getTaskIds") String taskID, @Arg("amount") int amount) {
            if (isInvalid(sender, player, taskID)) return;

            if (api.updateProgress(player, taskID, amount)) {
                logger.to(sender).info("Updated progress of task {} for player {} to {}", taskID, player.getName(), amount);
            } else {
                logger.to(sender).error("Failed to update progress of task {} for player {}", taskID, player.getName());
            }
        }

        @SubCommand(value = "increment", permission = "playertaskx.admin.progress.increment")
        public void increment(CommandSender sender, @Arg("player") Player player, @Arg(value = "taskID", suggestion = "getTaskIds") String taskID, @Arg("amount") int amount) {
            if (isInvalid(sender, player, taskID)) return;

            if (api.incrementTaskProgress(player.getUniqueId(), taskID, amount)) {
                logger.to(sender).info("Incremented progress of task {} for player {} by {}", taskID, player.getName(), amount);
            } else {
                logger.to(sender).error("Failed to increment progress of task {} for player {}", taskID, player.getName());
            }
        }

        @SubCommand(value = "complete", permission = "playertaskx.admin.progress.complete")
        public void complete(CommandSender sender, @Arg("player") Player player, @Arg(value = "taskID", suggestion = "getTaskIds") String taskID) {
            if (isInvalid(sender, player, taskID)) return;

            if (api.completeTask(player.getUniqueId(), taskID)) {
                logger.to(sender).info("Completed task {} for player {}", taskID, player.getName());
            } else {
                logger.to(sender).error("Failed to complete task {} for player {}", taskID, player.getName());
            }
        }

        @SubCommand(value = "reset", permission = "playertaskx.admin.progress.reset")
        public void reset(CommandSender sender, @Arg("player") Player player, @Arg(value = "taskID", suggestion = "getTaskIds") String taskID) {
            if (isInvalid(sender, player, taskID)) return;

            if (api.resetTask(player.getUniqueId(), taskID)) {
                logger.to(sender).info("Reset task {} for player {}", taskID, player.getName());
            } else {
                logger.to(sender).error("Failed to reset task {} for player {}", taskID, player.getName());
            }
        }

        @SubCommand(value = "info", permission = "playertaskx.admin.progress.info")
        public void info(CommandSender sender, @Arg("player") Player player, @Arg(value = "taskID", suggestion = "getTaskIds") String taskID) {
            if (isInvalid(sender, player, taskID)) return;

            boolean completed = api.isTaskCompleted(player.getUniqueId(), taskID);
            logger.to(sender).info("Task {} for player {} is {}", taskID, player.getName(), completed ? "completed" : "not completed");
        }

        public List<String> getTaskIds(CommandSender sender, cn.yvmou.ylib.command.context.CommandContext context, String input) {
            return cache.getTaskDefList().stream()
                    .map(com.playerPlugin.playerTaskX.api.model.TaskDefinition::getId)
                    .toList();
        }
    }

    private List<String> getTaskIds() {
        return cache.getTaskDefList().stream()
                .map(TaskDefinition::getId)
                .toList();
    }

    @SubCommand(value = "reload all", permission = "playertaskx.admin.reload_all")
    public void reloadCommand(CommandSender sender) {
        logger.to(sender).info("Reloading all");
    }

    private enum ReloadType {
        editor, command, storage
    }

    @SubCommand(value = "reload", permission = "playertaskx.admin.reload")
    public void reload(CommandSender sender, @Arg("type") ReloadType type) {
        switch (type) {
            case editor:
                logger.to(sender).info("Reloading editor");
                configurationManager.reloadConfiguration(EditorConfiguration.class);
                logger.to(sender).info("Configuration reloaded!");
                break;
            case command:
                logger.to(sender).info("Reloading command");
                commandManager.reload();
                logger.to(sender).info("Configuration reloaded!");
                logger.to(sender).warn("Attention! The main command cannot be fully unregistered dynamically. If you want to modify it, please reload the server!");
                break;
            case storage:
                logger.to(sender).info("Reloading storage");
                configurationManager.reloadConfiguration(StorgeConfiguration.class);
                logger.to(sender).info("Configuration reloaded!");
                break;
        }
    }

    private void sendHelp(CommandSender sender) {
        logger.to(sender).info("========= PlayerTaskX 管理员命令 =========");
        logger.to(sender).info("/ptxadmin help - 显示帮助信息");
        logger.to(sender).info("/ptxadmin task create <taskType> <taskID> - 创建任务");
        logger.to(sender).info("/ptxadmin task delete <taskID> - 删除任务");
        logger.to(sender).info("/ptxadmin task list - 显示服务器上所有可用任务");
        logger.to(sender).info("/ptxadmin progress start <player> <taskID> - 开始任务");
        logger.to(sender).info("/ptxadmin progress delete <player> <taskID> - 删除任务进度");
        logger.to(sender).info("/ptxadmin progress update <player> <taskID> <amount> - 更新任务进度");
        logger.to(sender).info("/ptxadmin progress increment <player> <taskID> <amount> - 增加任务进度");
        logger.to(sender).info("/ptxadmin progress complete <player> <taskID> - 完成任务");
        logger.to(sender).info("/ptxadmin progress reset <player> <taskID> - 重置任务");
        logger.to(sender).info("/ptxadmin progress info <player> <taskID> - 查看任务信息");
        logger.to(sender).info("/ptxadmin reload <type> - 重载插件配置");
    }

    private boolean isInvalid(CommandSender sender, Player player, String taskID) {
        if (player == null) {
            logger.to(sender).info("Please input a player");
            return true;
        }
        return isInvalid(sender, taskID);
    }

    private boolean isInvalid(CommandSender sender, String taskID) {
        if (taskID == null || taskID.isEmpty()) {
            logger.to(sender).info("Task ID cannot be empty");
            return true;
        }
        return false;
    }
}
