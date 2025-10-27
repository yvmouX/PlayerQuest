package com.playerPlugin.playerTaskX.PlayerTask.Trigger;

import org.bukkit.entity.Player;

public class TaskTriggerEco {
    /**
     * 处理资金
     *
     * @param player 选手
     * @param amount 量
     */
    protected static void handleMoney(Player player, String amount) {
        // TODO 处理vault经济
        try {
            int money = Integer.parseInt(amount);
        } catch (NumberFormatException e) {
            // 无效金额
        }
    }

    /**
     * 手柄点
     *
     * @param player 选手
     * @param amount 量
     */
    protected static void handlePoint(Player player, String amount) {
        // TODO 处理点券
        try {
            int points = Integer.parseInt(amount);
        } catch (NumberFormatException e) {
            // 无效数量
        }
    }

    /**
     * 手柄经验值
     *
     * @param player 选手
     * @param amount 量
     */
    protected static void handleXP(Player player, String amount) {
        try {
            int xp = Integer.parseInt(amount);
            if (xp > 0) {
                // TODO 测试此方法是否可以扣除负经验值
                player.giveExp(xp);
            } else {
                player.giveExp(xp);
            }
        } catch (NumberFormatException e) {
            // 无效经验值
        }
    }

    /**
     * 手柄水平
     *
     * @param player 选手
     * @param amount 量
     */
    protected static void handleLevel(Player player, String amount) {
        try {
            int level = Integer.parseInt(amount);
            if (level > 0) {
                player.setLevel(player.getLevel() + level);
            } else {
                // TODO 需要防止扣成负值吗？
                player.setLevel(Math.max(0, player.getLevel() + level));
            }
        } catch (NumberFormatException e) {
            // 无效等级
        }
    }
}
