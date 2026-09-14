package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import org.bukkit.entity.Player;

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

    @Override
    public boolean available() {
        return isAvailable();
    }

    /**
     * 经验是否可用。
     * <p>
     * 永远为 true：经验是原版资源，不需要任何插件，这正是它能作为兜底货币的原因。
     * 这个方法刻意独立存在——货币选择只看「该依赖是否存在」，
     * 而不是去读余额（那需要玩家在线，离屏场景会抛异常）。
     */
    public static boolean isAvailable() {
        return true;
    }

    /**
     * 玩家当前的总经验（跨等级累计）。
     * <p>
     * 刻意不读 {@code Player#getTotalExperience()}：那是与服务端「获得经验」路径绑定的独立计数器，
     * {@code setLevel()} / {@code setExp()}（{@link #take} 就是用它扣的）不会更新它，
     * 拿它算余额会与玩家看到的等级/进度对不上。Paper 的
     * {@code calculateTotalExperiencePoints()} 是同一个公式的正算，多留一条路径只会多一种
     * 可能不一致的来源，因此也不用（详见 {@link ExpUtil}）。
     */
    public static int totalExperience(Player player) {
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
}
