package com.playerPlugin.playerTaskX.commands.test;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.configs.TaskConfig;
import org.bukkit.command.CommandSender;

import static com.playerPlugin.playerTaskX.utils.Help.tm;

public class Info implements SubCommand {

    @Override
    @CommandOptions(
            name = "info", permission = "", onlyPlayer = true, alias = {}, register = true, usage = "/ptxtest info")
    public boolean execute(CommandSender sender, String[] args) {
        tm.getAllTasks().forEach(task -> {
            sender.sendMessage("Task " + task.toString());
        });

//        tm.getPlayerTasksMap().values().forEach(task -> {
//            sender.sendMessage("Map<UUID, List<PlayerTask>> " + task.toString());
//        });
        return false;
    }
}
