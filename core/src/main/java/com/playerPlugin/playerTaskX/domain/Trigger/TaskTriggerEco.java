package com.playerPlugin.playerTaskX.domain.Trigger;

import org.bukkit.entity.Player;

import static com.playerPlugin.playerTaskX.PlayerTaskX.getEconomy;
import static com.playerPlugin.playerTaskX.PlayerTaskX.getPlayerPointsAPI;
import static com.playerPlugin.playerTaskX.utils.Help.log;

public class TaskTriggerEco {
    /**
     * 处理资金
     *
     * @param player 选手
     * @param amount 量
     */
    protected static void handleMoney(Player player, String amount) {
        if (getEconomy() == null) {
            log.info("未安装Vault，无法使用经济功能");
            return;
        }

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
        if (getPlayerPointsAPI() == null) {
            log.info("未安装PlayerPoints，无法使用点券功能");
            return;
        }

        try {
            int points = Integer.parseInt(amount);

            if (points > 0) {
                getPlayerPointsAPI().give(player.getUniqueId(), points);
            } else {
                // 两个方法都只接受大于0的amount
                getPlayerPointsAPI().take(player.getUniqueId(), -points);
            }

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
                player.giveExpLevels(Math.max(0, player.getLevel() + level));
            }
        } catch (NumberFormatException e) {
            // 无效等级
        }
    }
}
