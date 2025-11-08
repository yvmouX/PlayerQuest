package com.playerPlugin.playerTaskX.commands.user;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static com.playerPlugin.playerTaskX.PlayerTask.TaskManager.tm;

/**
 * 接受 CMD
 *
 * <p>
 *     接受一个任务
 *     仅玩家可用
 * </p>
 *
 * @author yvmoux
 * &#064;date  2025/10/26
 */
public class AcceptCmd implements SubCommand {
    @Override
    @CommandOptions(name = "accept", permission = "playertaskx.command.accept", onlyPlayer = true, alias = {}, register = true, usage = "playertaskx accept <任务ID>")
    public boolean execute(CommandSender sender, String[] args) {
        accept(sender, args);
        return true;
    }

    private void accept(CommandSender sender, String[] args) {
        Player player = (Player) sender;


        String taskId = args[1];
        tm.startTask(player, taskId);

    }
}
