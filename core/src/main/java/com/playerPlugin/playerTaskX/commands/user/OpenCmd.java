package com.playerPlugin.playerTaskX.commands.user;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.UI.MainUI;
import me.devnatan.inventoryframework.ViewFrame;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OpenCmd implements SubCommand {
    private final ViewFrame viewFrame;

    public OpenCmd(ViewFrame viewFrame) {
        this.viewFrame = viewFrame;
    }
    @Override
    @CommandOptions(name = "open", permission = "playertaskx.command.open", onlyPlayer = true, alias = {}, register = true, usage = "/playertaskx open")
    public boolean execute(CommandSender sender, String[] args) {
        if (viewFrame != null) {
            viewFrame.open(MainUI.class, (Player) sender);
            sender.sendMessage("成功打开ui");
            return true;
        }
        sender.sendMessage("失败的man");
        return true;
    }
}
