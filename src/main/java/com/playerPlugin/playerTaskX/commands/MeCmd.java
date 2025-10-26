package com.playerPlugin.playerTaskX.commands;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import com.playerPlugin.playerTaskX.PlayerTask.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 我CMD
 *
 * <p>
 *     显示玩家（自己）已接取的任务
 *     仅玩家可用
 * </p>
 *
 * @author yvmoux
 * &#064;date  2025/10/26
 */
public class MeCmd implements SubCommand {
    @Override
    @CommandOptions(name = "me", permission = "playertaskx.command.me", onlyPlayer = true, alias = {}, register = true, usage = "/playertaskx me")
    public boolean execute(CommandSender sender, String[] args) {
        me(sender);
        return true;
    }

    private boolean me(CommandSender sender) {
        Player player = (Player) sender;

        TaskManager taskManager = TaskManager.getInstance();
        if (taskManager == null) {
            player.sendMessage("§c任务管理器未初始化！\n" +
                    "这是个不应该发生的错误，如果您看到这个消息，请及时服务器报告管理员！");
            return true;
        }

        List<PlayerTask> tasks = taskManager.getPlayerTasks(player.getUniqueId());

        if (tasks.isEmpty()) {
            player.sendMessage("§e你当前没有任务。");
            return true;
        }

        player.sendMessage("§6=== 你的任务列表 ===");
        for (PlayerTask pt : tasks) {
            String statusStr = switch (pt.getStatus()) {
                case IN_PROGRESS -> "§a进行中";
                case COMPLETED -> "§2已完成";
                case FAILED -> "§c失败";
                case UN_STARTED -> "§7未开始";
                default -> "§7未知";
            };

            player.sendMessage(String.format("§e%s §7- %s §7(%d/%d)",
                    pt.getTask().getName(),
                    statusStr,
                    pt.getProgress(),
                    pt.getTargetNumber()));
        }

        return true;
    }
}
