package com.playerPlugin.playerTaskX.PlayerTask.Trigger;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

public class TaskTriggerSpecial {
    /**
     * 处理杀戮
     *
     * @param player  选手
     * @param message 消息
     */
    protected static void handleKill(Player player, String message) {
        if (!message.isEmpty()) {
            player.sendMessage(message.replace("&", "§"));
        }
        player.setHealth(0);
    }

    /**
     * 手柄药水
     *
     * @param player 选手
     * @param args   参数
     */
    protected static void handlePotion(Player player, String args) {
        // TODO 处理药水效果
    }

    /**
     * 处理项目
     *
     * @param player 选手
     * @param args   参数
     */
    protected static void handleItem(Player player, String args) {
        // TODO 处理物品
    }
}
