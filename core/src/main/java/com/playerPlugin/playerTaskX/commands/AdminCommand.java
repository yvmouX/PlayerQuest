package com.playerPlugin.playerTaskX.commands;

import cn.yvmou.ylib.api.command.CommandManager;
import cn.yvmou.ylib.api.config.ConfigurationManager;
import cn.yvmou.ylib.api.logger.Logger;
import cn.yvmou.ylib.command.annotation.Arg;
import cn.yvmou.ylib.command.annotation.Command;
import cn.yvmou.ylib.command.annotation.Optional;
import cn.yvmou.ylib.command.annotation.SubCommand;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskType;
import com.playerPlugin.playerTaskX.api.TaskAPI;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.configuration.EditorConfiguration;
import com.playerPlugin.playerTaskX.configuration.StorgeConfiguration;
import org.bukkit.command.CommandSender;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

@Command(name = "playertaskxadmin", aliases = {"ptxadmin", "ptxa"}, description = "PlayerTaskX Admin Command", permission = "playertaskx.admin")
public class AdminCommand {
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

    @SubCommand(value = "create", permission = "playertaskx.admin.create")
    public void create(CommandSender sender,
                       @Arg("type") PTXTaskType taskType,
                       @Arg("id") String taskID,
                       @Optional @Arg("name") String taskName,
                       @Optional @Arg("description") String description) {
        if (!PTXTaskType.isValid(taskType)) {
            logger.to(sender).info("Invalid taskType");
            return;
        }

        if (taskID == null || taskID.isEmpty()) {
            logger.to(sender).info("Task ID cannot be empty");
            return;
        }

        if (taskName == null || taskName.isEmpty()) taskName = sender.getName() + "创建的任务";
        if (description == null || description.isEmpty()) description = "描述";

        TaskDefinition takDef = new TaskDefinition(taskID, taskType, taskName, description, List.of());

        api.createTask(takDef);
        logger.to(sender).info("Created successfully! id: {}, Type: {}, Name: {}, Description: {}", taskID, taskType, taskName, description);
    }

    @SubCommand(value = "delete", permission = "playertaskx.admin.delete")
    public void delete(CommandSender sender, @Arg("id") String taskID) {
        if (taskID == null) {
            logger.to(sender).info("Task ID cannot be empty");
            return;
        }

        api.deleteTask(taskID);
        logger.to(sender).info("Deleted successfully! id: {}", taskID);
    }

    @SubCommand(value = "list", permission = "playertaskx.admin.list", description = "list command")
    public void list(CommandSender sender) {
        sender.sendMessage("§6=== 所有可用任务 ===");
        Objects.requireNonNull(cache.getTaskDefList()).forEach(task -> {
            sender.sendMessage(String.format("§e%s §7- §f%s", task.getId(), task.getName()));
        });
    }


    @SubCommand(value = "reload all", permission = "playertaskx.admin.reload_all")
    public void reloadCommand(CommandSender sender) {
        logger.to(sender).info("Reloading all");
    }

    public enum ReloadType {
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
                // TODO
                logger.to(sender).info("Configuration reloaded!");
                break;
            case storage:
                logger.to(sender).info("Reloading storage");
                configurationManager.reloadConfiguration(StorgeConfiguration.class);
                logger.to(sender).info("Configuration reloaded!");
                break;
        }
    }

    @SubCommand(value = "debug reload_command", permission = "playertaskx.admin.debug")
    public void debugReloadCommand() {
        commandManager.reload();
    }

    private void sendHelp(CommandSender sender) {
        logger.to(sender).info("========= PlayerTaskX 管理员命令 =========");
        logger.to(sender).info("/ptxadmin help - 显示帮助信息");
        logger.to(sender).info("/ptxadmin create <taskType> <taskID> - 创建任务");
        logger.to(sender).info("/ptxadmin list - 显示服务器上所有可用任务");
        logger.to(sender).info("/ptxadmin start <player> <taskID> - 为玩家开始任务");
        logger.to(sender).info("/ptxadmin stop <player> <taskID> - 为玩家停止任务");
        logger.to(sender).info("/ptxadmin give <player> <taskID> - 为玩家给予任务");
        logger.to(sender).info("/ptxadmin take <player> <taskID> - 为玩家扣除任务");
        logger.to(sender).info("/ptxadmin complete <player> <taskID> - 为玩家完成任务");
        logger.to(sender).info("/ptxadmin reset <player> <taskID> - 为玩家重置任务进度");
        logger.to(sender).info("/ptxadmin list <player> - 显示玩家所有任务");
        logger.to(sender).info("/ptxadmin check <player> <taskID> - 显示玩家任务进度");
        logger.to(sender).info("/ptxadmin info - 显示插件信息");
        logger.to(sender).info("/ptxadmin reload - 重载插件");
    }

}
