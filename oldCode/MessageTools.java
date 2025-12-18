package cn.yvmou.ylib.tools;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class MessageTools {
    private Plugin plugin;

    public MessageTools(Plugin plugin) {
        this.plugin = plugin;
    }

    private String getConsolePrefix() {
        try {
            return "§8[§b§l§n" + plugin.getDescription().getName() + "§8]§r ";
        } catch (Exception e) {
            return "§8[§b§l§nYLib§r] ";
        }
    }

    public void info(Player player, String msg) {
        info(player, ChatColor.GREEN, msg); // 默认使用绿色
    }
    public void info(CommandSender sender, String msg) { info(sender, ChatColor.GREEN, msg);}

    /**
     * color info
     *
     * @param color 颜色
     * @param msg 消息
     */
    public void info(Player player, ChatColor color, String msg) {
        player.sendMessage(getConsolePrefix() + (color == null ? ChatColor.GREEN : color) + msg);
    }
    public void info(CommandSender sender, ChatColor color, String msg, Object... args) {
        sender.sendMessage(getConsolePrefix() + (color == null ? ChatColor.GREEN : color) + msg);
    }

    /**
     * warn
     *
     * @param msg 颜色
     */
    public void warn(Player player, String msg) {
        warn(player, ChatColor.GOLD, msg);
    }
    public void warn(CommandSender sender, String msg) { warn(sender, ChatColor.GOLD, msg); }

    /**
     * color warn
     *
     * @param color 颜色
     * @param msg 消息
     */
    public void warn(Player player, ChatColor color, String msg) {
        player.sendMessage(getConsolePrefix() + (color == null ? ChatColor.GOLD : color) + msg);
    }
    public void warn(CommandSender sender, ChatColor color, String msg) {
        sender.sendMessage(getConsolePrefix() + (color == null ? ChatColor.GOLD : color) + msg);
    }

    /**
     * err
     *
     * @param msg 消息
     */
    public void error(Player player, String msg) {
        error(player, ChatColor.RED, msg);
    }
    public void error(CommandSender sender, String msg) { error(sender, ChatColor.RED, msg); }

    /**
     * color err
     *
     * @param color 颜色
     * @param msg 消息
     */
    public void error(Player player, ChatColor color, String msg) {
        player.sendMessage(getConsolePrefix() + (color == null ? ChatColor.RED : color) + msg);
    }
    public void error(CommandSender sender, ChatColor color, String msg) {
        sender.sendMessage(getConsolePrefix() + (color == null ? ChatColor.RED : color) + msg);
    }
}
