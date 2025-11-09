package com.playerPlugin.playerTaskX.commands.user;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

import static com.playerPlugin.playerTaskX.utils.Help.sm;
import static com.playerPlugin.playerTaskX.utils.Help.tm;

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

    private void me(CommandSender sender) {
        Player player = (Player) sender;

        List<PlayerTask> tasks = sm.getPlayerTaskCache().getPlayerInProgressTasks(player.getUniqueId());

        if (tasks == null || tasks.isEmpty()) {
            player.sendMessage("§e你当前没有任务。");
            return;
        }

        player.sendMessage("§6=== 你的任务列表 ===");

        for (PlayerTask pt : tasks) {
            String statusStr = switch (pt.getStatus()) {
                case IN_PROGRESS -> "§a进行中";
                case COMPLETED -> "§2已完成";
                case FAILED -> "§c失败";
                default -> "§7未知";
            };

            List<String> b = new ArrayList<>();

            pt.getTask().getTargets().forEach((target) -> {
                String finishStr = target.isFinished() ? "§a[已完成]" : "§c[未完成]";
                String a = String.format("  §7→ 目标%d: 材料%s §7(需%d/%d个) %s",
                        target.getIndex(),
                        target.getRequirement().getMaterial().name(),
                        target.getCurrent(),
                        target.getRequirement().getAmount(),
                        finishStr
                );
                b.add(a);
            });

            player.sendMessage(String.format("§e%s §7- %s", pt.getTask().getName(), statusStr));
            for (String a : b) {
                player.sendMessage(a);
            }
            player.sendMessage("");
        }
    }
}