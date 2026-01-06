package com.playerPlugin.playerTaskX.Trigger;

import cn.yvmou.ylib.api.logger.Logger;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.entity.Player;

public class TaskTriggerEco {
    private static Logger log;
    private static Economy economy;
    private static PlayerPointsAPI ppAPI;

    public TaskTriggerEco(Logger log, Economy economy, PlayerPointsAPI ppAPI) {
        TaskTriggerEco.log = log;
        TaskTriggerEco.economy = economy;
        TaskTriggerEco.ppAPI = ppAPI;
    }

    /**
     * 处理资金
     *
     * @param player 选手
     * @param amount 量
     */
    protected static void handleMoney(Player player, String amount) {
        if (economy == null) {
            log.info("未安装Vault，无法使用经济功能");
            return;
        }

        try {
            int money = Integer.parseInt(amount);

            if (money > 0) {
                economy.depositPlayer(player, money);
            } else {
                economy.withdrawPlayer(player, money);
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
        if (ppAPI == null) {
            log.info("未安装PlayerPoints，无法使用点券功能");
            return;
        }

        try {
            int points = Integer.parseInt(amount);

            if (points > 0) {
                ppAPI.give(player.getUniqueId(), points);
            } else {
                // 两个方法都只接受大于0的amount
                ppAPI.take(player.getUniqueId(), -points);
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
