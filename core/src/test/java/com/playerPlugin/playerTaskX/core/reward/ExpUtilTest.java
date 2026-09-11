package com.playerPlugin.playerTaskX.core.reward;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 原版经验换算测试。
 *
 * <p>这块逻辑算错会直接吞掉玩家的经验（扣多）或让余额判断失真（扣错），
 * 因此按原版公式逐段验证，并检查「扣除后剩余」与原版行为一致。</p>
 *
 * <p>公式来自 Minecraft 原版：0→L 级所需经验为
 * {@code L≤16: L²+6L}、{@code 17≤L≤31: 2.5L²-40.5L+360}、{@code L≥32: 4.5L²-162.5L+2220}。</p>
 */
class ExpUtilTest {

    @Test
    @DisplayName("等级对应的累计经验符合原版公式（三段分界处）")
    void totalExpForLevelMatchesVanilla() {
        assertEquals(0, ExpUtil.totalExpForLevel(0));
        // 0→16 段：16² + 6×16 = 352
        assertEquals(352, ExpUtil.totalExpForLevel(16));
        // 17 级处于第二段：2.5×289 - 40.5×17 + 360 = 394
        assertEquals(394, ExpUtil.totalExpForLevel(17));
        // 第二段末：31 级
        assertEquals((int) Math.floor(2.5 * 31 * 31 - 40.5 * 31 + 360), ExpUtil.totalExpForLevel(31));
        // 32 级进入第三段
        assertEquals((int) Math.floor(4.5 * 32 * 32 - 162.5 * 32 + 2220), ExpUtil.totalExpForLevel(32));
        // 30 级是常被引用的参考值：825
        assertEquals((int) Math.floor(2.5 * 30 * 30 - 40.5 * 30 + 360), ExpUtil.totalExpForLevel(30));
    }

    @Test
    @DisplayName("累计经验随等级单调递增")
    void totalExpIsMonotonic() {
        int previous = -1;
        for (int level = 0; level <= 60; level++) {
            int current = ExpUtil.totalExpForLevel(level);
            assertTrue(current > previous, "等级 " + level + " 的累计经验应大于上一级");
            previous = current;
        }
    }

    @Test
    @DisplayName("级内进度参与换算：0.5 表示半级")
    void totalExperienceIncludesProgress() {
        int level = 10;
        int needed = ExpUtil.expNeededForNextLevel(level);

        assertEquals(ExpUtil.totalExpForLevel(level), ExpUtil.totalExperience(level, 0.0f));
        assertEquals(ExpUtil.totalExpForLevel(level) + needed / 2, ExpUtil.totalExperience(level, 0.5f));
        // 进度被夹在 [0, 1) 之间：1.0 不应越到下一级
        assertEquals(ExpUtil.totalExpForLevel(level + 1) - 1, ExpUtil.totalExperience(level, 1.0f));
    }

    @Test
    @DisplayName("扣除后等级与级内余量正确（跨级扣除）")
    void afterRemovingCrossesLevels() {
        int level = 30;
        int all = ExpUtil.totalExperience(level, 0.0f);

        // 扣掉从 0 到 29 级的全部经验，应恰好剩在 29 级起点
        int[] after = ExpUtil.afterRemoving(level, 0.0f, all - ExpUtil.totalExpForLevel(29));
        assertEquals(29, after[0], "应降到 29 级");
        assertEquals(0, after[1], "级内不应有剩余经验");
    }

    @Test
    @DisplayName("扣除不会产生负数：扣光后停在 0 级 0 经验")
    void afterRemovingClampsAtZero() {
        int[] after = ExpUtil.afterRemoving(50, 0.8f, Integer.MAX_VALUE);
        assertEquals(0, after[0]);
        assertEquals(0, after[1]);
    }

    @Test
    @DisplayName("扣少量经验：只减少级内进度，等级不变")
    void afterRemovingKeepsLevelWhenEnoughInLevel() {
        int level = 20;
        int needed = ExpUtil.expNeededForNextLevel(level);
        // 从半级扣掉 10 点
        int[] after = ExpUtil.afterRemoving(level, 0.5f, 10);

        assertEquals(level, after[0], "级内经验足够时不应降级");
        assertEquals(needed / 2 - 10, after[1], "级内余量应减少相应点数");
    }

    @Test
    @DisplayName("级内余量换算成 setExp 需要的 0.0~1.0 进度")
    void expProgressConversion() {
        int level = 10;
        int needed = ExpUtil.expNeededForNextLevel(level);

        assertEquals(0.0f, ExpUtil.expProgress(0, level));
        // 注意：级内所需经验可能是奇数（10 级为 55），因此「一半」不是精确的 0.5。
        // 断言用实际比例而不是理想值，避免把测试写成对实现的错误期待
        assertEquals((float) (needed / 2) / needed, ExpUtil.expProgress(needed / 2, level));
        // 满级内经验应接近 1 但不会被当作已升级
        assertTrue(ExpUtil.expProgress(needed, level) < 1.0f);
        assertTrue(ExpUtil.expProgress(needed, level) > 0.99f);
    }

    @Test
    @DisplayName("总经验反查等级与正推一致（往返一致）")
    void levelForTotalExperienceRoundTrips() {
        for (int level = 0; level <= 50; level++) {
            int total = ExpUtil.totalExpForLevel(level);
            assertEquals(level, ExpUtil.levelForTotalExperience(total),
                    "总经验 " + total + " 应反查出等级 " + level);
            // 差一点就不该算升级
            if (level > 0) {
                assertEquals(level - 1, ExpUtil.levelForTotalExperience(total - 1));
            }
        }
    }

    @Test
    @DisplayName("负数与零输入不抛异常")
    void handlesEdgeInputs() {
        assertEquals(0, ExpUtil.totalExpForLevel(-5));
        assertEquals(0, ExpUtil.totalExperience(-1, -1.0f));
        assertEquals(0, ExpUtil.levelForTotalExperience(-100));
        assertEquals(0.0f, ExpUtil.expProgress(0, 0));
    }
}
