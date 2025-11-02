package com.playerPlugin.playerTaskX.commands.user;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

        TaskManager taskManager = TaskManager.getInstance();
        if (taskManager == null) {
            player.sendMessage("§c任务管理器未初始化！\n" +
                    "这是个不应该发生的错误，如果您看到这个消息，请及时服务器报告管理员！");
            return;
        }

        String taskId = args[1];
        taskManager.startTask(player, taskId);

    }
}
