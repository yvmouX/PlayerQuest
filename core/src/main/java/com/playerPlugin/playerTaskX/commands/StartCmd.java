//package com.playerPlugin.playerTaskX.commands.admin;
//
//import cn.yvmou.ylib.api.command.*;
//import com.playerPlugin.playerTaskX.api.TaskAPI;
//import com.playerPlugin.playerTaskX.cache.TaskCache;
//import org.bukkit.Bukkit;
//import org.bukkit.ChatColor;
//import org.bukkit.command.CommandSender;
//import org.bukkit.entity.Player;
//
//import java.util.Objects;
//
//public class StartCmd implements SubCommand {
//    private final TaskCache cache;
//    private final TaskAPI taskAPI;
//
//    public StartCmd(TaskCache cache, TaskAPI taskAPI) {
//        this.cache = cache;
//        this.taskAPI = taskAPI;
//    }
//
//    @Override
//    @CommandOptions(
//            name = "start",
//            permission = "playertaskx.admin.command.start",
//            onlyPlayer = true,
//            alias = {},
//            register = true,
//            usage = "/playertaskxadmin start <taskID> <player>"
//    )
//    @CommandComplete({
//            @CommandComplete.Tab(
//                    type = CompleteType.CUSTOM,
//                    customOptions = {"<taskID>"}),
//            @CommandComplete.Tab(
//                    type = CompleteType.PRESET,
//                    preset = PresetType.ONLINE_PLAYER
//            )
//    })
//    public boolean execute(CommandSender sender, String[] args) {
//        if (Objects.equals(args[0], "start") && args.length != 3) {
//            sender.sendMessage(ChatColor.RED + "Usage: /playertaskxadmin start <taskID> <player>");
//            return false;
//        }
//
//        String taskID = args[1];
//        if (cache.getTaskDef(taskID) == null) {
//            sender.sendMessage(ChatColor.RED + "No such task: " + taskID);
//            return false;
//        }
//
//        Player p = Bukkit.getPlayer(args[2]);
//        if (p == null) {
//            sender.sendMessage(ChatColor.RED + "Player not found.");
//            return false;
//        }
//
//        try {
//            taskAPI.createProgress(p, taskID);
//        } catch (Exception e) {
//            sender.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
//            throw new RuntimeException(e);
//        }
//
//        return true;
//    }
//}
