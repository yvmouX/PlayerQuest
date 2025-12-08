package com.playerPlugin.playerTaskX.commands.admin;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import org.bukkit.command.CommandSender;

public class ReloadCmd implements SubCommand {

    @Override
    @CommandOptions(name = "reload_all", permission = "playertaskx.admin.command.reload", onlyPlayer = false, alias = {}, register = true, usage = "/playertaskx reload")
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage("未实现!");
        return true;
    }
}
