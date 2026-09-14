package com.playerPlugin.playerTaskX.core.reward;

import org.bukkit.entity.Player;

/**
 * 经验作为「刷新费用」的兜底货币。
 *
 * <h2>它为什么不再是奖励类型</h2>
 * 奖励只保留金币 / 点券 / 命令三种：要发经验用命令奖励（{@code xp add %player% 200 points}）
 * 就够了，多做一种奖励类型只是多一套要维护的字段与文档。但刷新费用仍需要一条
 * 「任何服务端都成立」的兜底——金币要经济插件、点券要 PlayerPoints，都没有时
 * 刷新不该直接变成不可用，因此经验这条链留着（见 {@link CurrencyType}）。
 *
 * <h2>余额从哪里来</h2>
 * 不用 {@code Player#getTotalExperience()}：它是与服务端「获得经验」路径绑定的独立计数器，
 * {@code setLevel()} / {@code setExp()}（{@link #take} 正是用这两个方法扣的）不会更新它，
 * 拿它算余额会与玩家看到的等级/进度条对不上。理由与公式见 {@link ExpUtil}。
 */
public final class ExpCurrency {

    private ExpCurrency() {
    }

    /**
     * 经验永远可用。
     * <p>
     * 与其他货币不同，这里不看任何插件：经验是原版资源，这正是它能当兜底的原因。
     */
    public static boolean available() {
        return true;
    }

    /** 玩家当前的总经验（跨等级累计）。 */
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
