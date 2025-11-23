package com.playerPlugin.playerTaskX.commands.admin;

import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.SubCommand;
import com.playerPlugin.infra.TaskManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Objects;

public class StartCmd implements SubCommand {
    private final TaskManager tm;
    public StartCmd(TaskManager tm) {
        this.tm = tm;
    }

    @Override
    @CommandOptions(
            name = "start",
            permission = "playertaskx.admin.command.start",
            onlyPlayer = true,
            alias = {},
            register = true,
            usage = "/playertaskxadmin start <taskID> <player>"
    )
    public boolean execute(CommandSender sender, String[] args) {
        if (Objects.equals(args[0], "start") && args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /playertaskxadmin start <taskID> <player>");
            return false;
        }

        String taskID = args[1];
        if (tm.getTask(taskID) == null) {
            sender.sendMessage(ChatColor.RED + "No such task: " + taskID);
            return false;
        }

        Player p = Bukkit.getPlayer(args[2]);
        if (p == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return false;
        }

        try {
            tm.getTaskProgressManger().startTask(p, taskID);
        } catch (SQLException e) {
            sender.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
            throw new RuntimeException(e);
        }

        return true;
    }
}
