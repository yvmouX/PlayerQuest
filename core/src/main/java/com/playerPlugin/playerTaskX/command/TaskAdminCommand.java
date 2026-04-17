package com.playerPlugin.playerTaskX.command;

import cn.yvmou.ylib.command.annotation.Command;
import cn.yvmou.ylib.command.annotation.SubCommand;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.command.CommandSender;

@Command(name = "playertaskxadmin", description = "PlayerTaskX admin commands")
public class TaskAdminCommand {

    private final TaskManager taskManager;

    public TaskAdminCommand(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @SubCommand("")
    public void help(CommandSender sender) {
        sender.sendMessage("=== PlayerTaskX Admin Commands ===");
        sender.sendMessage("/taskadmin reload - Reload task configurations");
    }

    @SubCommand("reload")
    public void reload(CommandSender sender) {
        taskManager.loadTasks();
        sender.sendMessage("Tasks reloaded!");
    }
}
