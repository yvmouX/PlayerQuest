package com.playerPlugin.playerTaskX.domain.PlayerTask.Trigger;

import org.bukkit.entity.Player;

import static com.playerPlugin.playerTaskX.PlayerTaskX.getEconomy;

public class TaskTriggerEco {
    /**
     * 处理资金
     *
     * @param player 选手
     * @param amount 量
     */
    protected static void handleMoney(Player player, String amount) {
        try {
            int money = Integer.parseInt(amount);

            if (money > 0) {
                getEconomy().depositPlayer(player, money);
            } else {
                getEconomy().withdrawPlayer(player, money);
            }

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
            player.giveExp(xp);
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
                player.giveExpLevels(player.getLevel() + level);
            } else {
                // TODO 需要防止扣成负值吗？
                // TODO 大概需要，防着点
                player.giveExpLevels(Math.max(0, player.getLevel() + level));
            }
        } catch (NumberFormatException e) {
            // 无效等级
        }
    }
}
