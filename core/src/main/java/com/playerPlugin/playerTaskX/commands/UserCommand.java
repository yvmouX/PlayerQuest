package com.playerPlugin.playerTaskX.commands;

import cn.yvmou.ylib.api.logger.Logger;
import cn.yvmou.ylib.command.annotation.Command;
import cn.yvmou.ylib.command.annotation.SubCommand;
import com.playerPlugin.playerTaskX.TaskAPI;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@Command(name = "playertaskx", aliases = {"ptx"}, description = "PlayerTaskX User Command", permission = "playertaskx.command")
public class UserCommand {

    private final Logger log;
    private final TaskAPI api;

    public UserCommand(Logger log, TaskAPI api) {
        this.log = log;
        this.api = api;
    }

    @SubCommand("")
    public void noArgs(CommandSender sender) {
        sendHelp(sender);
    }

    @SubCommand(value = "help", permission = "playertaskx.command.help")
    public void help(CommandSender sender) {
        sendHelp(sender);
    }

    @SubCommand(value = "me", permission = "playertaskx.command.me")
    public void me(CommandSender sender) {
        if (!(sender instanceof Player)) {
            log.to(sender).info("This command can only be used by players.");
            return;
        }

        Player player = (Player) sender;
        List<TaskProgress> tasks = api.getPlayerTasks(player);

        log.to(sender).info("§6=== 我的任务 ===");
        if (tasks.isEmpty()) {
            log.to(sender).info("§7你当前没有正在进行的任务。");
            return;
        }

        for (TaskProgress task : tasks) {
            String statusStr = switch (task.getStatus()) {
                case IN_PROGRESS -> "§a进行中";
                case COMPLETED -> "§2已完成";
                case FAILED -> "§c失败";
                default -> "§7未知";
            };

            // 任务标题行
            // 此时我们需要从缓存中获取任务定义来显示名字和目标
            api.getTaskDefinition(task.getTaskId()).ifPresentOrElse(def -> {
                sender.sendMessage(String.format("§e%s §7- %s", def.getName(), statusStr));
                
                // 目标详情行
                for (int i = 0; i < def.getObjectives().size(); i++) {
                    String objectiveId = def.getObjectives().get(i);
                    var objective = api.getObjectiveDefinition(objectiveId).orElse(null);
                    if (objective == null) continue;

                    int currentAmount = task.getObjectiveAmount(objectiveId);
                    boolean isFinished = currentAmount >= objective.getTargetAmount();
                    String finishStr = isFinished ? "§a[已完成]" : "§c[未完成]";
                    
                    String detail = String.format("  §7→ 目标: %s §7(%d/%d) %s", 
                            objective.getTarget(), 
                            currentAmount, 
                            objective.getTargetAmount(), 
                            finishStr
                    );
                    sender.sendMessage(detail);
                }
            }, () -> {
                sender.sendMessage(String.format("§e未知任务(ID:%s) §7- %s", task.getTaskId(), statusStr));
            });
            
            // 任务间空行分隔
            sender.sendMessage("");
        }
    }

    private void sendHelp(CommandSender sender) {
        log.to(sender).info("========= PlayerTaskX 玩家命令 =========");
        log.to(sender).info("/ptx help - 显示帮助信息");
        log.to(sender).info("/ptx me - 查看我的任务");
    }
}
