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

            TaskDefinition takDef = new TaskDefinition(taskID, taskType, taskName, description, new java.util.ArrayList<>(), new java.util.ArrayList<>());

            api.createTask(takDef);
            logger.to(sender).info("Created successfully! id: {}, Type: {}, Name: {}, Description: {}", taskID, taskType, taskName, description);
        }

        @SubCommand(value = "add objective", permission = "playertaskx.admin.task_add_objective")
        public void addObjective(CommandSender sender, 
                                 @Arg(value = "id", suggestion = "getTaskIds") String taskID,
                                 @Arg(value = "objectiveId", suggestion = "getObjectiveIds") String objectiveId) {
            if (isInvalid(sender, taskID)) return;

            TaskDefinition taskDef = api.getTaskDefinition(taskID).orElse(null);
            if (taskDef == null) {
                logger.to(sender).error("Task with ID {} does not exist", taskID);
                return;
            }

            com.playerPlugin.playerTaskX.api.model.ObjectiveDefinition objDef = api.getObjectiveDefinition(objectiveId).orElse(null);
            if (objDef == null) {
                logger.to(sender).error("Objective with ID {} does not exist", objectiveId);
                return;
            }

            // 获取现有的 objectives 并添加新的 ID
            List<String> objectives = new java.util.ArrayList<>(taskDef.getObjectives());
            objectives.add(objectiveId);

            // 创建新的 TaskDefinition（因为它是不可变的）
            TaskDefinition updatedDef = new TaskDefinition(
                    taskDef.getId(), 
                    taskDef.getType(), 
                    taskDef.getName(), 
                    taskDef.getDescription(), 
                    objectives,
                    taskDef.getRewards()
            );

            // 更新到缓存和存储
            api.deleteTask(taskID);
            api.createTask(updatedDef);
            
            logger.to(sender).info("Added objective {} to task {} successfully!", objectiveId, taskID);
        }

        @SubCommand(value = "add reward", permission = "playertaskx.admin.task_add_reward")
        public void addReward(CommandSender sender, 
                                 @Arg(value = "id", suggestion = "getTaskIds") String taskID,
                                 @Arg(value = "rewardId", suggestion = "getRewardIds") String rewardId) {
            if (isInvalid(sender, taskID)) return;

            TaskDefinition taskDef = api.getTaskDefinition(taskID).orElse(null);
            if (taskDef == null) {
                logger.to(sender).error("Task with ID {} does not exist", taskID);
                return;
            }

            com.playerPlugin.playerTaskX.api.model.RewardDefinition rewardDef = api.getRewardDefinition(rewardId).orElse(null);
            if (rewardDef == null) {
                logger.to(sender).error("Reward with ID {} does not exist", rewardId);
                return;
            }

            // 获取现有的 rewards 并添加新的 ID
            List<String> rewards = new java.util.ArrayList<>(taskDef.getRewards());
            rewards.add(rewardId);

            // 创建新的 TaskDefinition（因为它是不可变的）
            TaskDefinition updatedDef = new TaskDefinition(
                    taskDef.getId(), 
                    taskDef.getType(), 
                    taskDef.getName(), 
                    taskDef.getDescription(), 
                    taskDef.getObjectives(),
                    rewards
            );

            // 更新到缓存和存储
            api.deleteTask(taskID);
            api.createTask(updatedDef);
            
            logger.to(sender).info("Added reward {} to task {} successfully!", rewardId, taskID);
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

        public List<String> getObjectiveIds(CommandSender sender, cn.yvmou.ylib.command.context.CommandContext context, String input) {
            return cache.getObjectiveDefList().stream()
                    .map(com.playerPlugin.playerTaskX.api.model.ObjectiveDefinition::getId)
                    .toList();
        }
    }

    // 2. Objective Group
    @SubCommand("objective")
    public class ObjectiveGroup {
        
        @SubCommand(value = "create", permission = "playertaskx.admin.objective.create")
        public void create(CommandSender sender,
                           @Arg("id") String id,
                           @Arg("action") com.playerPlugin.playerTaskX.api.Enum.PTXActionType action,
                           @Arg("target") String target,
                           @Arg("amount") int amount) {
            if (isInvalid(sender, id)) return;

            com.playerPlugin.playerTaskX.api.model.ObjectiveDefinition def = new com.playerPlugin.playerTaskX.api.model.ObjectiveDefinition(id, action, target, amount);
            
            if (api.createObjective(def)) {
                logger.to(sender).info("Objective {} created successfully", id);
            } else {
                logger.to(sender).error("Failed to create objective {}", id);
            }
        }

        @SubCommand(value = "delete", permission = "playertaskx.admin.objective.delete")
        public void delete(CommandSender sender, @Arg(value = "id", suggestion = "getObjectiveIds") String id) {
            if (isInvalid(sender, id)) return;

            if (api.deleteObjective(id)) {
                logger.to(sender).info("Objective {} deleted successfully", id);
            } else {
                logger.to(sender).error("Failed to delete objective {}", id);
            }
        }

        @SubCommand(value = "list", permission = "playertaskx.admin.objective.list")
        public void list(CommandSender sender) {
            sender.sendMessage("§6=== 所有可用目标 ===");
            cache.getObjectiveDefList().forEach(obj -> {
                sender.sendMessage(String.format("§e%s §7- 动作:%s 目标:%s 数量:%d", obj.getId(), obj.getAction(), obj.getTarget(), obj.getTargetAmount()));
            });
        }

        public List<String> getObjectiveIds(CommandSender sender, cn.yvmou.ylib.command.context.CommandContext context, String input) {
            return cache.getObjectiveDefList().stream()
                    .map(com.playerPlugin.playerTaskX.api.model.ObjectiveDefinition::getId)
                    .toList();
        }
    }

    // 3. Reward Group
    @SubCommand("reward")
    public class RewardGroup {

        @SubCommand(value = "create", permission = "playertaskx.admin.reward.create")
        public void create(CommandSender sender,
                           @Arg("id") String id,
                           @Arg("type") com.playerPlugin.playerTaskX.api.Enum.PTXRewardType type,
                           @Arg("content") String content,
                           @Arg("amount") int amount) {
            if (isInvalid(sender, id)) return;

            com.playerPlugin.playerTaskX.api.model.RewardDefinition def = new com.playerPlugin.playerTaskX.api.model.RewardDefinition(id, type, content, amount);

            if (api.createReward(def)) {
                logger.to(sender).info("Reward {} created successfully", id);
            } else {
                logger.to(sender).error("Failed to create reward {}", id);
            }
        }

        @SubCommand(value = "delete", permission = "playertaskx.admin.reward.delete")
        public void delete(CommandSender sender, @Arg(value = "id", suggestion = "getRewardIds") String id) {
            if (isInvalid(sender, id)) return;

            if (api.deleteReward(id)) {
                logger.to(sender).info("Reward {} deleted successfully", id);
            } else {
                logger.to(sender).error("Failed to delete reward {}", id);
            }
        }

        @SubCommand(value = "list", permission = "playertaskx.admin.reward.list")
        public void list(CommandSender sender) {
            sender.sendMessage("§6=== 所有可用奖励 ===");
            cache.getRewardDefList().forEach(obj -> {
                sender.sendMessage(String.format("§e%s §7- 类型:%s 内容:%s 数量:%d", obj.getId(), obj.getType(), obj.getContent(), obj.getAmount()));
            });
        }

        public List<String> getRewardIds(CommandSender sender, cn.yvmou.ylib.command.context.CommandContext context, String input) {
            return cache.getRewardDefList().stream()
                    .map(com.playerPlugin.playerTaskX.api.model.RewardDefinition::getId)
                    .toList();
        }
    }

    // 4. Progress Group
    @SubCommand("progress")
    public class ProgressGroup {
        
        @SubCommand(value = "start", permission = "playertaskx.admin.progress.start")
        public void start(CommandSender sender, @Arg("player") Player player, @Arg(value = "taskID", suggestion = "getTaskIds") String taskID) {
            if (isInvalid(sender, player, taskID)) return;

            if (!hasObjective(taskID)) {
                logger.to(sender).error("Task {} does not have objectives", taskID);
                return;
            }
            
            if (api.createProgress(player, taskID)) {
                logger.to(sender).info("Started task {} for player {}", taskID, player.getName());
            } else {
                logger.to(sender).error("Failed to start task {} for player {}", taskID, player.getName());
            }
        }

        @SubCommand(value = "delete", permission = "playertaskx.admin.progress.delete")
        public void delete(CommandSender sender, @Arg("player") Player player, @Arg(value = "taskID", suggestion = "getTaskIds") String taskID) {
            if (isInvalid(sender, player, taskID)) return;

            if (!hasObjective(taskID)) {
                logger.to(sender).error("Task {} does not have objectives", taskID);
                return;
            }

            if (api.deleteProgress(player, taskID)) {
                logger.to(sender).info("Deleted progress of task {} for player {}", taskID, player.getName());
            } else {
                logger.to(sender).error("Failed to delete progress of task {} for player {}", taskID, player.getName());
            }
        }

        @SubCommand(value = "update", permission = "playertaskx.admin.progress.update")
        public void update(CommandSender sender, @Arg("player") Player player, @Arg(value = "taskID", suggestion = "getTaskIds") String taskID, @Arg("amount") int amount) {
            if (isInvalid(sender, player, taskID)) return;

            if (!hasObjective(taskID)) {
                logger.to(sender).error("Task {} does not have objectives", taskID);
                return;
            }

            if (api.updateProgress(player, taskID, amount)) {
                logger.to(sender).info("Updated progress of task {} for player {} to {}", taskID, player.getName(), amount);
            } else {
                logger.to(sender).error("Failed to update progress of task {} for player {}", taskID, player.getName());
            }
        }

        @SubCommand(value = "increment", permission = "playertaskx.admin.progress.increment")
        public void increment(CommandSender sender, @Arg("player") Player player, @Arg(value = "taskID", suggestion = "getTaskIds") String taskID, @Arg(value = "objectiveId", suggestion = "getTaskObjectiveIds") String objectiveId, @Arg("amount") int amount) {
            if (isInvalid(sender, player, taskID)) return;

            if (!hasObjective(taskID)) {
                logger.to(sender).error("Task {} does not have objectives", taskID);
                return;
            }

            if (api.incrementTaskProgress(player.getUniqueId(), taskID, objectiveId, amount)) {
                logger.to(sender).info("Incremented progress of objective {} in task {} for player {} by {}", objectiveId, taskID, player.getName(), amount);
            } else {
                logger.to(sender).error("Failed to increment progress of objective {} in task {} for player {}", objectiveId, taskID, player.getName());
            }
        }

        @SubCommand(value = "complete", permission = "playertaskx.admin.progress.complete")
        public void complete(CommandSender sender, @Arg("player") Player player, @Arg(value = "taskID", suggestion = "getTaskIds") String taskID) {
            if (isInvalid(sender, player, taskID)) return;

            if (!hasObjective(taskID)) {
                logger.to(sender).error("Task {} does not have objectives", taskID);
                return;
            }

            if (api.completeTask(player.getUniqueId(), taskID)) {
                logger.to(sender).info("Completed task {} for player {}", taskID, player.getName());
            } else {
                logger.to(sender).error("Failed to complete task {} for player {}", taskID, player.getName());
            }
        }

        @SubCommand(value = "reset", permission = "playertaskx.admin.progress.reset")
        public void reset(CommandSender sender, @Arg("player") Player player, @Arg(value = "taskID", suggestion = "getTaskIds") String taskID) {
            if (isInvalid(sender, player, taskID)) return;

            if (!hasObjective(taskID)) {
                logger.to(sender).error("Task {} does not have objectives", taskID);
                return;
            }

            if (api.resetTask(player.getUniqueId(), taskID)) {
                logger.to(sender).info("Reset task {} for player {}", taskID, player.getName());
            } else {
                logger.to(sender).error("Failed to reset task {} for player {}", taskID, player.getName());
            }
        }

        @SubCommand(value = "info", permission = "playertaskx.admin.progress.info")
        public void info(CommandSender sender, @Arg("player") Player player, @Arg(value = "taskID", suggestion = "getTaskIds") String taskID) {
            if (isInvalid(sender, player, taskID)) return;

            if (!hasObjective(taskID)) {
                logger.to(sender).error("Task {} does not have objectives", taskID);
                return;
            }

            boolean completed = api.isTaskCompleted(player.getUniqueId(), taskID);
            logger.to(sender).info("Task {} for player {} is {}", taskID, player.getName(), completed ? "completed" : "not completed");
        }

        public List<String> getTaskIds(CommandSender sender, cn.yvmou.ylib.command.context.CommandContext context, String input) {
            return cache.getTaskDefList().stream()
                    .map(com.playerPlugin.playerTaskX.api.model.TaskDefinition::getId)
                    .toList();
        }
        public List<String> getTaskObjectiveIds(CommandSender sender, cn.yvmou.ylib.command.context.CommandContext context, String input) {
            String taskID = context.get("taskID");
            
            // Fallback: 如果 context 中获取不到 taskID，尝试从 rawArgs 中获取
            // 假设参数顺序中 taskID 紧挨着当前补全参数的前面
            if (taskID == null) {
                String[] args = context.rawArgs();
                if (args != null && args.length >= 2) {
                    // args 的最后一个元素通常是当前正在输入的参数（input），倒数第二个就是前一个参数
                    taskID = args[args.length - 2];
                }
            }

            if (taskID == null) return java.util.Collections.emptyList();
            
            return api.getTaskDefinition(taskID)
                    .map(com.playerPlugin.playerTaskX.api.model.TaskDefinition::getObjectives)
                    .orElse(java.util.Collections.emptyList());
        }
    }

    private List<String> getTaskIds() {
        return cache.getTaskDefList().stream()
                .map(TaskDefinition::getId)
                .toList();
    }
    // 第二种写法
    // @SubCommand(value = "reload all", permission = "playertaskx.admin.reload_all")
    // public void reloadCommand(CommandSender sender) {
    //     logger.to(sender).info("Reloading all");
    // }

    // private enum ReloadType {
    //     editor, command, storage
    // }

    @SubCommand(value = "reload", permission = "playertaskx.admin.reload")
    public void reload(CommandSender sender, @Arg("type") ReloadType type) {
        if (type == null) {
            logger.to(sender).info("Please specify a reload type: editor, command, storage, all");
            return;
        }
        
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
                // 使缓存的自动保存任务响应最新配置
                cache.reloadAutoSave();
                break;
            case all:
                logger.to(sender).info("Reloading all");
                configurationManager.reloadConfiguration(EditorConfiguration.class);
                commandManager.reload();
                configurationManager.reloadConfiguration(StorgeConfiguration.class);
                cache.reloadAutoSave();
                logger.to(sender).info("All configurations reloaded!");
                break;
        }
    }

    private enum ReloadType {
        editor, command, storage, all
    }

    private void sendHelp(CommandSender sender) {
        logger.to(sender).info("========= PlayerTaskX 管理员命令 =========");
        logger.to(sender).info("/ptxadmin help - 显示帮助信息");
        logger.to(sender).info("/ptxadmin task create <taskType> <taskID> - 创建任务");
        logger.to(sender).info("/ptxadmin task add objective <taskID> <objectiveId> - 为任务添加目标");
        logger.to(sender).info("/ptxadmin task delete <taskID> - 删除任务");
        logger.to(sender).info("/ptxadmin task list - 显示服务器上所有可用任务");
        logger.to(sender).info("/ptxadmin objective create <id> <action> <target> <amount> - 创建目标");
        logger.to(sender).info("/ptxadmin objective delete <id> - 删除目标");
        logger.to(sender).info("/ptxadmin objective list - 显示服务器上所有可用目标");
        logger.to(sender).info("/ptxadmin reward create <id> <type> <content> <amount> - 创建奖励");
        logger.to(sender).info("/ptxadmin reward delete <id> - 删除奖励");
        logger.to(sender).info("/ptxadmin reward list - 显示服务器上所有可用奖励");
        logger.to(sender).info("/ptxadmin task add reward <taskID> <rewardId> - 为任务添加奖励");
        logger.to(sender).info("/ptxadmin progress start <player> <taskID> - 开始任务");
        logger.to(sender).info("/ptxadmin progress delete <player> <taskID> - 删除任务进度");
        logger.to(sender).info("/ptxadmin progress update <player> <taskID> <amount> - 更新任务进度");
        logger.to(sender).info("/ptxadmin progress increment <player> <taskID> <objectiveId> <amount> - 增加任务指定目标进度");
        logger.to(sender).info("/ptxadmin progress complete <player> <taskID> - 完成任务");
        logger.to(sender).info("/ptxadmin progress reset <player> <taskID> - 重置任务");
        logger.to(sender).info("/ptxadmin progress info <player> <taskID> - 查看任务信息");
        logger.to(sender).info("/ptxadmin reload <type> - 重载插件配置 (type: editor, command, storage, all)");
    }

    private boolean hasObjective(String taskID) {
        TaskDefinition taskDef = api.getTaskDefinition(taskID).orElse(null);
        return taskDef != null && taskDef.getObjectives() != null && !taskDef.getObjectives().isEmpty();
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
