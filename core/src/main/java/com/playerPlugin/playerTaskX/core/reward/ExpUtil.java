package com.playerPlugin.playerTaskX.core.reward;

/**
 * 原版经验值的换算。
 *
 * <h2>为什么不用 {@code Player#getTotalExperience()}</h2>
 * 它不是废弃 API（spigot-api 1.21.8 与 paper-api 26.2 都没有标 {@code @Deprecated}），
 * 但它是<b>另一个计数器</b>：CraftPlayer 直接返回 {@code ServerPlayer.totalExperience} 这个字段，
 * 而该字段只有原版的「获得经验」路径会维护（{@code giveExperiencePoints}、
 * {@code giveExperienceLevels}、附魔），Bukkit 的 {@code setLevel(int)} / {@code setExp(float)}
 * 只写等级与级内进度、不碰它——我们扣费正是用这两个方法写的。
 * 拿它当余额会立刻与玩家看到的等级/进度条不一致，而「判断用一个来源、写入改另一个来源」
 * 的误差只会累积。
 *
 * <p>因此余额一律从「等级 + 级内进度」算（本类就是这条公式），扣完之后的等级与进度再从总量反推。
 * 反推没有现成 API 可用（Paper 的 {@code calculateTotalExperiencePoints()} 只是把同一个公式正算一遍），
 * 只能自己实现。
 *
 * <p>公式（与原版一致）：从 0 级升到 {@code L} 级所需经验为
 * <ul>
 *   <li>{@code L ≤ 16}：{@code L² + 6L}</li>
 *   <li>{@code 17 ≤ L ≤ 31}：{@code 2.5L² - 40.5L + 360}</li>
 *   <li>{@code L ≥ 32}：{@code 4.5L² - 162.5L + 2220}</li>
 * </ul>
 * 当前级内进度用 {@code exp}（0.0~1.0）乘以该级所需经验。
 *
 * <p>本类是纯函数，便于直接单元测试——经验算法写错会直接吞掉玩家的经验，
 * 属于必须验证的逻辑。
 *
 * @author yvmou
 * @since 1.0.0
 */
public final class ExpUtil {

    private ExpUtil() {
    }

    /** 从 0 级升到指定等级所需的总经验。 */
    public static int totalExpForLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            // 原版为 2.5L² - 40.5L + 360，用整数运算避免浮点误差
            return (int) Math.floor(2.5 * level * level - 40.5 * level + 360);
        }
        return (int) Math.floor(4.5 * level * level - 162.5 * level + 2220);
    }

    /** 升到下一级还需要多少经验（即当前级升满所需的总量）。 */
    public static int expNeededForNextLevel(int level) {
        return totalExpForLevel(level + 1) - totalExpForLevel(level);
    }

    /**
     * 指定等级与级内进度对应的总经验。
     *
     * @param level 等级
     * @param exp   级内进度，0.0~1.0
     */
    public static int totalExperience(int level, float exp) {
        int safeLevel = Math.max(0, level);
        float safeExp = Math.max(0.0f, Math.min(0.999f, exp));
        return totalExpForLevel(safeLevel) + (int) Math.floor(safeExp * expNeededForNextLevel(safeLevel));
    }

    /**
     * 扣除经验后的等级与级内进度。
     *
     * @param level  当前等级
     * @param exp    当前级内进度
     * @param amount 要扣除的总经验
     * @return 长度 2 的数组：{@code [新等级, 新级内进度对应的总经验余量]}
     */
    public static int[] afterRemoving(int level, float exp, int amount) {
        int current = totalExperience(level, exp);
        int remaining = Math.max(0, current - Math.max(0, amount));
        int newLevel = levelForTotalExperience(remaining);
        int leftoverInLevel = remaining - totalExpForLevel(newLevel);
        return new int[]{newLevel, leftoverInLevel};
    }

    /** 总经验对应的等级（不超过该总经验的最高等级）。 */
    public static int levelForTotalExperience(int total) {
        int level = 0;
        int safeTotal = Math.max(0, total);
        while (totalExpForLevel(level + 1) <= safeTotal) {
            level++;
        }
        return level;
    }

    /**
     * 把级内余量换算成 {@code setExp} 需要的 0.0~1.0 进度。
     *
     * @param remainingInLevel 级内已有经验
     * @param level            所在等级
     */
    public static float expProgress(int remainingInLevel, int level) {
        int needed = expNeededForNextLevel(level);
        if (needed <= 0) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(0.999f, (float) remainingInLevel / needed));
    }
}
