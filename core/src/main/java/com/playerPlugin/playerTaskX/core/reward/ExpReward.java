package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 奖励：经验值。
 * <p>
 * 也用于「没有经济插件时的兜底货币」：当服务器既没装 Vault 也没装 PlayerPoints 时，
 * 刷新每日任务的费用会从经验里扣（见 {@code CurrencyType}）。
 * 经验是原版资源，任何服务端都有，因此这个兜底总是可用。
 */
public final class ExpReward implements RewardType {

    public static final String ID = "exp";

    /** Paper 的原生总经验方法；解析一次后缓存，null 表示不可用。 */
    private static Method calculateMethod;
    private static boolean calculateResolved;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "经验";
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.integer("amount", "经验点数", 100, "发放的经验点数（不是等级）")
        );
    }

    @Override
    public void grant(Player player, QuestReward reward) {
        int amount = reward.integer("amount", 0);
        if (amount > 0) {
            player.giveExp(amount);
        }
    }

    /** 玩家当前的总经验（跨等级累计）。 */
    public static int totalExperience(Player player) {
        Method method = resolveCalculate();
        if (method != null) {
            try {
                Object result = method.invoke(player);
                if (result instanceof Number number) {
                    return Math.max(0, number.intValue());
                }
            } catch (ReflectiveOperationException ignored) {
                // 反射失败则退回原版公式
            }
        }
        return ExpUtil.totalExperience(player.getLevel(), player.getExp());
    }

    /**
     * 扣除经验，余额不足时返回 false 且不做任何改动。
     * <p>
     * 扣减逻辑与原版一致：先把当前等级降到 0 级，不足的部分再从上一级继续扣。
     */
    public static boolean take(Player player, int amount) {
        int cost = Math.max(0, amount);
        if (cost == 0) {
            return true;
        }
        int current = totalExperience(player);
        if (current < cost) {
            return false;
        }
        int[] after = ExpUtil.afterRemoving(player.getLevel(), player.getExp(), cost);
        player.setLevel(after[0]);
        player.setExp(ExpUtil.expProgress(after[1], after[0]));
        return true;
    }

    /** 解析 Paper 的 {@code calculateTotalExperiencePoints()}，失败返回 null。 */
    private static Method resolveCalculate() {
        if (calculateResolved) {
            return calculateMethod;
        }
        calculateResolved = true;
        try {
            calculateMethod = Player.class.getMethod("calculateTotalExperiencePoints");
        } catch (NoSuchMethodException e) {
            // 非 Paper 系服务端：使用原版公式，结果一致
            calculateMethod = null;
        }
        return calculateMethod;
    }
}
