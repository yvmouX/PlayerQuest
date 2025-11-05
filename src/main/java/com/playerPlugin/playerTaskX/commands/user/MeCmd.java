package com.playerPlugin.playerTaskX.commands.user;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.Requirement;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.TaskTarget;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.playerPlugin.playerTaskX.PlayerTask.TaskManager.tm;

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

        List<PlayerTask> tasks = tm.getPlayerTaskCache().getPlayerInProgressTasks(player.getUniqueId());

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

            Map<TaskTarget, List<Requirement>> targetRequiresMap = new LinkedHashMap<>();
            Map<Integer, Requirement> requirementsMap = new LinkedHashMap<>();

            pt.getTask().getTargets().forEach(target -> {
                targetRequiresMap.put(target, target.getRequires());
                target.getRequires().forEach(requires -> {
                    requirementsMap.put(requires.getIndex(), requires);
                });
            });


            AtomicReference<String> b = new AtomicReference<>("");

            targetRequiresMap.forEach((target, requires) -> {
                requirementsMap.forEach((index, req) -> {
                    String finishStr = req.isFinished() ? "§a[已完成]" : "§c[未完成]";
                    String a = String.format("  §7→ 目标%d: 材料%s §7(需%d/%d个) %s",
                            index,
                            req.getMaterial().name(),
                            req.getCurrent(),
                            req.getAmount(),
                            finishStr
                    );
                    b.set(a);
                });
            });

            player.sendMessage(String.format("§e%s §7- %s\n %s",
                    pt.getTask().getName(),
                    statusStr,
                    b.get()
            ));
        }
    }
}