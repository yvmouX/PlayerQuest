package com.playerPlugin.playerTaskX.commands.admin;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import com.playerPlugin.infra.TaskManager;
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
    private final TaskManager tm;
    public ListCmd(TaskManager tm) {
        this.tm = tm;
    }
    @Override
    @CommandOptions(name = "list", permission = "playertaskx.admin.command.list", onlyPlayer = false, alias = {}, register = true, usage = "/ptxa list")
    public boolean execute(CommandSender sender, String[] args) {
        tasks(sender);
        return true;
    }

    private boolean tasks(CommandSender sender) {
        sender.sendMessage("§6=== 所有可用任务 ===");
        tm.getAllTasks().forEach(task -> {
            sender.sendMessage(String.format("§e%s §7- §f%s", task.getId(), task.getName()));
        });

        return true;
    }
}
