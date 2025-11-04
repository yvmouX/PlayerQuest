package com.playerPlugin.playerTaskX.commands.admin;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import org.bukkit.command.CommandSender;

/**
 * 列表 cmd
 *
 * <p>
 *     显示所有已定义的任务
 * </p>
 *
 * @author yvmoux
 * &#064;date  2025/10/26
 */
public class ListCmd implements SubCommand {
    @Override
    @CommandOptions(name = "list", permission = "playertaskx.admin.command.list", onlyPlayer = false, alias = {}, register = true, usage = "/ptxa list")
    public boolean execute(CommandSender sender, String[] args) {
        tasks(sender);
        return true;
    }

    private boolean tasks(CommandSender sender) {
        TaskManager taskManager = TaskManager.getInstance();
        if (taskManager == null) {
            sender.sendMessage("§c任务系统未初始化！");
            return true;
        }

        sender.sendMessage("§6=== 所有可用任务 ===");
        taskManager.getTasksMap().forEach((id, task) -> {
            sender.sendMessage(String.format("§e%s §7- §f%s", id, task.getName()));
        });

        return true;
    }
}
