package com.playerPlugin.bukkit.commands.user;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import org.bukkit.command.CommandSender;

public class HelpCmd implements SubCommand {
    @Override
    @CommandOptions(name = "help", permission = "playertaskx.command.help", onlyPlayer = false, alias = {}, register = true, usage = "playertaskx help")
    public boolean execute(CommandSender sender, String[] strings) {
        if (strings[0].equals("2")) {
            helpAdmin(sender);
        } else {
            helpUser(sender);
        }
        return false;
    }

    private void helpUser(CommandSender sender) {
        sender.sendMessage("§6=== 帮助(1/2) - 玩家命令 ===");
        sender.sendMessage("§e/ptxa help §7- §f显示帮助");
        sender.sendMessage("§e/ptxa me §7- §f显示我的任务列表");
        sender.sendMessage("§e/ptxa open §7- §f打开任务菜单");
        sender.sendMessage("§e/ptxa accept <任务ID> §7- §f接受一个任务");
    }

    private void helpAdmin(CommandSender sender) {
        sender.sendMessage("§6=== 帮助(2/2) - 管理命令 ===");
        sender.sendMessage("§e/ptxa list §7- §f显示所有已定义的任务");
        sender.sendMessage("§e/ptxa reload §7- §f重载配置文件");
        sender.sendMessage("§e/ptxa start <任务ID> §7- §f启动指定任务");
    }
}
