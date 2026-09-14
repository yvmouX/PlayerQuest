package com.playerPlugin.playerTaskX.core.period;

import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.config.PeriodSettings;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 周期标识与下次重置时刻的计算（纯函数，便于测试）。
 *
 * <h2>周期标识是什么</h2>
 * 一串<b>只由「哪一段时间」决定</b>的字符串，用来判断「玩家这一周期是否已经发过任务」：
 * 同一段周期内重登、换服、掉线重连都得到同一个值，因此不会重复发放，也不会靠重连刷任务。
 * 每种周期各有自己的形状，互不冲突（同一个玩家可以同时有每日与每周任务）：
 *
 * <table border="1">
 *   <caption>周期标识</caption>
 *   <tr><th>类型</th><th>形状</th><th>例子</th></tr>
 *   <tr><td>每日</td><td>{@code yyyy-MM-dd}</td><td>{@code 2026-09-14}</td></tr>
 *   <tr><td>每周</td><td>{@code W + 本周起始日}</td><td>{@code W2026-09-14}（周一开始）</td></tr>
 *   <tr><td>每月</td><td>{@code yyyy-MM}</td><td>{@code 2026-09}</td></tr>
 *   <tr><td>自定义</td><td>{@code C<周期>#<序号>}</td><td>{@code C3d#6893}</td></tr>
 * </table>
 *
 * <h2>重置时刻的语义</h2>
 * 每日/每周/每月都先按 {@code reset-hour} 把时间往前挪：早于重置时刻算作上一个周期，
 * 因此重置点为 04:00 时，某日 03:30 与前一天 23:00 属于同一周期（「熬夜到凌晨还算今天」）。
 * 自定义周期没有「几点重置」这回事，它按<b>固定锚点</b>（Unix 纪元）取整，跨服、跨时区一致。
 */
public final class Periods {

    private Periods() {
    }

    /** 该周期的标识；传入不适用的类型（NORMAL）时返回空串。 */
    public static String periodOf(QuestType type, PeriodSettings settings, LocalDateTime moment) {
        return switch (type) {
            case DAILY -> day(moment, settings.resetHour()).toString();
            case WEEKLY -> "W" + weekStart(moment, settings).toString();
            case MONTHLY -> monthKey(moment, settings);
            case CUSTOM -> customKey(settings, moment);
            case NORMAL -> "";
        };
    }

    /** 下一次重置时刻（毫秒时间戳），永远在未来。 */
    public static long nextResetMillis(QuestType type, PeriodSettings settings, LocalDateTime moment) {
        return switch (type) {
            case DAILY -> day(moment, settings.resetHour()).plusDays(1)
                    .atTime(settings.resetHour(), 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            case WEEKLY -> weekStart(moment, settings).plusWeeks(1)
                    .atTime(settings.resetHour(), 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            case MONTHLY -> nextMonthStart(moment, settings)
                    .atTime(settings.resetHour(), 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            case CUSTOM -> (customBucket(settings, moment) + 1) * customMillis(settings);
            case NORMAL -> 0L;
        };
    }

    /** 该周期在界面上怎么称呼。 */
    public static String label(QuestType type) {
        return switch (type) {
            case DAILY -> "每日";
            case WEEKLY -> "每周";
            case MONTHLY -> "每月";
            case CUSTOM -> "自定义周期";
            case NORMAL -> "普通";
        };
    }

    // ------------------------------------------------------------------
    // 各周期的算法
    // ------------------------------------------------------------------

    /** 按重置小时把时刻归到「哪一天」。 */
    private static LocalDate day(LocalDateTime moment, int resetHour) {
        LocalDate date = moment.toLocalDate();
        return moment.getHour() < resetHour ? date.minusDays(1) : date;
    }

    /** 本周期从哪个日期开始（每周的重置星期几，含重置小时）。 */
    private static LocalDate weekStart(LocalDateTime moment, PeriodSettings settings) {
        LocalDate date = day(moment, settings.resetHour());
        DayOfWeek anchor = DayOfWeek.of(settings.resetWeekday());
        // 往回找到最近的一个锚点日；正好是锚点日就取当天
        int back = (date.getDayOfWeek().getValue() - anchor.getValue() + 7) % 7;
        return date.minusDays(back);
    }

    /** 每月：还没到本月的重置日就算上个月。 */
    private static String monthKey(LocalDateTime moment, PeriodSettings settings) {
        LocalDate date = day(moment, settings.resetHour());
        LocalDate start = date.getDayOfMonth() >= settings.resetMonthDay()
                ? date.withDayOfMonth(settings.resetMonthDay())
                : date.minusMonths(1).withDayOfMonth(settings.resetMonthDay());
        return String.format("%04d-%02d", start.getYear(), start.getMonthValue());
    }

    /** 下个月的重置日（本月的重置日还没到就取本月）。 */
    private static LocalDate nextMonthStart(LocalDateTime moment, PeriodSettings settings) {
        LocalDate date = day(moment, settings.resetHour());
        LocalDate start = date.getDayOfMonth() >= settings.resetMonthDay()
                ? date.withDayOfMonth(settings.resetMonthDay())
                : date.minusMonths(1).withDayOfMonth(settings.resetMonthDay());
        return start.plusMonths(1);
    }

    /** 自定义周期：把「纪元以来的毫秒数」按周期长度分桶，桶号就是周期的身份。 */
    private static long customBucket(PeriodSettings settings, LocalDateTime moment) {
        long millis = moment.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return Math.floorDiv(millis, customMillis(settings));
    }

    private static String customKey(PeriodSettings settings, LocalDateTime moment) {
        return "C" + settings.period() + "#" + customBucket(settings, moment);
    }

    /**
     * 自定义周期的长度（毫秒）。
     * <p>
     * 支持 {@code <n>d}（天）与 {@code <n>h}（小时）；写坏了退回 3 天——
     * 一个拼错的配置不该让周期任务彻底不工作。
     */
    static long customMillis(PeriodSettings settings) {
        String raw = settings.period().toLowerCase(java.util.Locale.ROOT);
        long unit;
        if (raw.endsWith("d")) {
            unit = 24L * 60 * 60 * 1000;
        } else if (raw.endsWith("h")) {
            unit = 60L * 60 * 1000;
        } else {
            return 3L * 24 * 60 * 60 * 1000;
        }
        try {
            long value = Long.parseLong(raw.substring(0, raw.length() - 1).trim());
            return value <= 0 ? 3L * 24 * 60 * 60 * 1000 : value * unit;
        } catch (NumberFormatException e) {
            return 3L * 24 * 60 * 60 * 1000;
        }
    }
}
