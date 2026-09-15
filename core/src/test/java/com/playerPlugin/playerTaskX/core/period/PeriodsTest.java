package com.playerPlugin.playerTaskX.core.period;

import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.config.PeriodSettings;
import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 周期算法测试（纯函数，不需要服务端）：防「同一周期被当成两个周期」或反过来——前者会让玩家一个周期里被发两次任务，后者让他永远拿不到新任务，两者都不会报错。
 */
class PeriodsTest {

    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    @DisplayName("每日：早于重置时刻算前一天（熬夜到凌晨还算今天）")
    void dailyResetHour() {
        PeriodSettings settings = settings(QuestType.DAILY);
        // 04:00 重置：凌晨 3:30 仍属于前一天
        assertEquals("2026-09-09", Periods.periodOf(QuestType.DAILY, settings, LocalDateTime.of(2026, 9, 10, 3, 30)));
        assertEquals("2026-09-10", Periods.periodOf(QuestType.DAILY, settings, LocalDateTime.of(2026, 9, 10, 4, 0)));
        assertEquals("2026-09-10", Periods.periodOf(QuestType.DAILY, settings, LocalDateTime.of(2026, 9, 10, 23, 59)));
        assertEquals("2026-09-11", Periods.periodOf(QuestType.DAILY, settings, LocalDateTime.of(2026, 9, 11, 4, 0)));
    }

    @Test
    @DisplayName("每日：重置小时为 0 时按自然日划分")
    void dailyMidnightReset() {
        PeriodSettings settings = with("resetHour", 0, QuestType.DAILY);
        assertEquals("2026-09-10", Periods.periodOf(QuestType.DAILY, settings, LocalDateTime.of(2026, 9, 10, 0, 0)));
        assertEquals("2026-09-09",
                Periods.periodOf(QuestType.DAILY, settings, LocalDateTime.of(2026, 9, 10, 0, 0).minusMinutes(1)));
    }

    @Test
    @DisplayName("每日：下次重置恰好是下一个重置点（相差一天）")
    void dailyNextReset() {
        PeriodSettings settings = settings(QuestType.DAILY);
        long beforeReset = Periods.nextResetMillis(QuestType.DAILY, settings, LocalDateTime.of(2026, 9, 10, 3, 30));
        long afterReset = Periods.nextResetMillis(QuestType.DAILY, settings, LocalDateTime.of(2026, 9, 10, 4, 30));

        assertEquals(DAY, afterReset - beforeReset, "3:30 的下一次是当天 4:00，4:30 的下一次是次日 4:00");
    }

    @Test
    @DisplayName("每周：同一周内每天都是同一个周期，跨到锚点日才换")
    void weeklySameWeek() {
        PeriodSettings settings = settings(QuestType.WEEKLY);
        // 默认锚点是周一 04:00；2026-09-14 是周一
        assertEquals("W2026-09-14", Periods.periodOf(QuestType.WEEKLY, settings, LocalDateTime.of(2026, 9, 14, 4, 0)));
        assertEquals("W2026-09-14", Periods.periodOf(QuestType.WEEKLY, settings, LocalDateTime.of(2026, 9, 17, 12, 0)));
        assertEquals("W2026-09-14", Periods.periodOf(QuestType.WEEKLY, settings, LocalDateTime.of(2026, 9, 20, 23, 0)));
        // 周一凌晨 3:30 仍算上一周
        assertEquals("W2026-09-07", Periods.periodOf(QuestType.WEEKLY, settings, LocalDateTime.of(2026, 9, 14, 3, 30)));
        assertEquals("W2026-09-21", Periods.periodOf(QuestType.WEEKLY, settings, LocalDateTime.of(2026, 9, 21, 4, 0)));
    }

    @Test
    @DisplayName("每周：换一种锚点日就以那天为一周的开始")
    void weeklyCustomWeekday() {
        // 锚点改成周四：2026-09-17 是周四，因此 09-17 ~ 09-23 属于同一周
        PeriodSettings settings = with("resetWeekday", "THURSDAY", QuestType.WEEKLY);
        assertEquals("W2026-09-17", Periods.periodOf(QuestType.WEEKLY, settings, LocalDateTime.of(2026, 9, 17, 4, 0)));
        assertEquals("W2026-09-17", Periods.periodOf(QuestType.WEEKLY, settings, LocalDateTime.of(2026, 9, 23, 23, 0)));
        assertEquals("W2026-09-24", Periods.periodOf(QuestType.WEEKLY, settings, LocalDateTime.of(2026, 9, 24, 4, 0)));
    }

    @Test
    @DisplayName("每周：下次重置落在未来")
    void weeklyNextReset() {
        PeriodSettings settings = settings(QuestType.WEEKLY);
        LocalDateTime moment = LocalDateTime.of(2026, 9, 17, 12, 0);
        long next = Periods.nextResetMillis(QuestType.WEEKLY, settings, moment);
        long now = moment.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertTrue(next > now, "必须是未来时刻");
        assertTrue(next - now <= 7 * DAY, "不能超过一个周期长度");
    }

    @Test
    @DisplayName("每月：到重置日才换月，重置日之前仍算上个月")
    void monthlyKey() {
        PeriodSettings settings = settings(QuestType.MONTHLY);
        assertEquals("2026-09", Periods.periodOf(QuestType.MONTHLY, settings, LocalDateTime.of(2026, 9, 1, 4, 0)));
        assertEquals("2026-09", Periods.periodOf(QuestType.MONTHLY, settings, LocalDateTime.of(2026, 9, 30, 23, 0)));
        // 10-01 03:30 早于重置时刻 → 仍算 9 月
        assertEquals("2026-09", Periods.periodOf(QuestType.MONTHLY, settings, LocalDateTime.of(2026, 10, 1, 3, 30)));
        assertEquals("2026-10", Periods.periodOf(QuestType.MONTHLY, settings, LocalDateTime.of(2026, 10, 1, 4, 0)));
    }

    @Test
    @DisplayName("每月：跨年时月份标识不重复（12 月 → 次年 1 月）")
    void monthlyAcrossYearBoundary() {
        PeriodSettings settings = settings(QuestType.MONTHLY);
        assertEquals("2026-12", Periods.periodOf(QuestType.MONTHLY, settings, LocalDateTime.of(2026, 12, 20, 12, 0)));
        assertEquals("2027-01", Periods.periodOf(QuestType.MONTHLY, settings, LocalDateTime.of(2027, 1, 1, 4, 0)));
    }

    @Test
    @DisplayName("自定义周期：按固定锚点分桶，长度内是同一个周期")
    void customBuckets() {
        PeriodSettings settings = settings(QuestType.CUSTOM);
        assertEquals("3d", settings.period(), "默认 3 天");

        String first = Periods.periodOf(QuestType.CUSTOM, settings, LocalDateTime.of(2026, 9, 10, 12, 0));
        String sameBucket = Periods.periodOf(QuestType.CUSTOM, settings, LocalDateTime.of(2026, 9, 11, 12, 0));
        String nextBucket = Periods.periodOf(QuestType.CUSTOM, settings, LocalDateTime.of(2026, 9, 13, 12, 0));

        assertTrue(first.startsWith("C3d#"), first);
        assertEquals(first, sameBucket, "3 天内的两次调用必须是同一个周期");
        assertNotEquals(first, nextBucket);
    }

    @Test
    @DisplayName("自定义周期：下次重置落在未来，且不超过一个周期长度")
    void customNextReset() {
        PeriodSettings settings = settings(QuestType.CUSTOM);
        LocalDateTime moment = LocalDateTime.of(2026, 9, 10, 12, 0);
        long next = Periods.nextResetMillis(QuestType.CUSTOM, settings, moment);
        long now = moment.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertTrue(next > now, "必须是未来时刻");
        assertTrue(next - now <= 3 * DAY, "不能超过一个周期长度");
    }

    @Test
    @DisplayName("自定义周期：12 小时这种短周期也认；写坏的长度退回 3 天而不是让功能失效")
    void customLengthParsing() {
        assertEquals(3 * DAY, Periods.customMillis(settings(QuestType.CUSTOM)));
        assertEquals(12L * 60 * 60 * 1000, Periods.customMillis(with("period", "12h", QuestType.CUSTOM)));
        assertEquals(7 * DAY, Periods.customMillis(with("period", "7D", QuestType.CUSTOM)));

        assertEquals(3 * DAY, Periods.customMillis(with("period", "3x", QuestType.CUSTOM)));
        assertEquals(3 * DAY, Periods.customMillis(with("period", "abc", QuestType.CUSTOM)));
        assertEquals(3 * DAY, Periods.customMillis(with("period", "0d", QuestType.CUSTOM)), "0 也是无效长度");
    }

    @Test
    @DisplayName("普通任务没有周期（周期标识为空串）")
    void normalHasNoPeriod() {
        assertEquals("", Periods.periodOf(QuestType.NORMAL, settings(QuestType.NORMAL), LocalDateTime.now()));
        assertEquals(0L, Periods.nextResetMillis(QuestType.NORMAL, settings(QuestType.NORMAL), LocalDateTime.now()));
    }

    // ---------------------------------------------------------------- 辅助

    private static PeriodSettings settings(QuestType type) {
        return new PluginConfig().periodic(type);
    }

    /**
     * 造一个改过某个字段的配置。
     * <p>
     * 用反射而不是给 {@code PeriodSettings} 加 setter：那些字段只由 YLib 在加载配置时写入，
     * 为了测试在生产类上开一堆 setter 不划算。
     */
    private static PeriodSettings with(String field, Object value, QuestType type) {
        try {
            PeriodSettings settings = new PluginConfig().periodic(type);
            java.lang.reflect.Field target = PeriodSettings.class.getDeclaredField(field);
            target.setAccessible(true);
            target.set(settings, value);
            return settings;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("无法构造测试用配置: " + e.getMessage(), e);
        }
    }
}
