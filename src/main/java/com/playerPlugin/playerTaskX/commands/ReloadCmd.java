package com.playerPlugin.playerTaskX.commands;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import org.bukkit.command.CommandSender;

public class ReloadCmd implements SubCommand {
    private final PlayerTaskX plugin;

    public ReloadCmd(PlayerTaskX plugin) {
        this.plugin = plugin;
    }

    @Override
    @CommandOptions(name = "reload_all", permission = "playertaskx.command.reload", onlyPlayer = false, alias = {}, register = true, usage = "/playertaskx reload")
    public boolean execute(CommandSender sender, String[] args) {
        plugin.reloadConfig();
        sender.sendMessage("Config reloaded!");
        return true;
    }
}
