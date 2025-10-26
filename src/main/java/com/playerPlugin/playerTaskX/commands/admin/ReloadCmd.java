package com.playerPlugin.playerTaskX.commands.admin;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.configs.TaskConfig;
import org.bukkit.command.CommandSender;

public class ReloadCmd implements SubCommand {
    private final PlayerTaskX plugin;
    private final TaskConfig taskConfig;

    public ReloadCmd(PlayerTaskX plugin, TaskConfig taskConfig) {
        this.plugin = plugin;
        this.taskConfig = taskConfig;
    }

    @Override
    @CommandOptions(name = "reload_all", permission = "playertaskx.command.reload", onlyPlayer = false, alias = {}, register = true, usage = "/playertaskx reload")
    public boolean execute(CommandSender sender, String[] args) {
        plugin.reloadConfig();
        taskConfig.reloadTasksConfig();
        sender.sendMessage("Config reloaded!");
        return true;
    }
}
