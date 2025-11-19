package com.playerPlugin.infra.commands.user;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import com.playerPlugin.infra.PlayerTaskX;
import com.playerPlugin.infra.UI.MainUI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OpenCmd implements SubCommand {
    @Override
    @CommandOptions(name = "open", permission = "playertaskx.command.open", onlyPlayer = true, alias = {}, register = true, usage = "/playertaskx open")
    public boolean execute(CommandSender sender, String[] args) {
        if (PlayerTaskX.getViewFrame() != null) {
            PlayerTaskX.getViewFrame().open(MainUI.class, (Player) sender);
            sender.sendMessage("成功打开ui");
            return true;
        }
        sender.sendMessage("失败的man");
        return true;
    }
}
