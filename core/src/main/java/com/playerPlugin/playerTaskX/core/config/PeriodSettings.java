package com.playerPlugin.playerTaskX.core.config;

import cn.yvmou.ylib.config.ConfigValue;
import com.playerPlugin.playerTaskX.api.model.QuestType;

import java.util.List;
import java.util.Locale;

/** 一种周期任务的配置（{@code periodic.daily/weekly/monthly/custom}）：四种周期共用一个类，只用各自相关的字段，无关字段保持默认。 */
public class PeriodSettings {

    @ConfigValue("enabled")
    private boolean enabled;

    @ConfigValue("amount")
    private int amount = 3;

    @ConfigValue("reset-hour")
    private int resetHour = 4;

    @ConfigValue("reset-weekday")
    private String resetWeekday = "MONDAY";

    @ConfigValue("reset-month-day")
    private int resetMonthDay = 1;

    @ConfigValue("period")
    private String period = "3d";

    @ConfigValue("refresh-cost")
    private double refreshCost;

    @ConfigValue("refresh-limit")
    private int refreshLimit;

    @ConfigValue("pool")
    private List<String> pool = List.of();

    /** 内置默认：每日任务（保持与旧版本 {@code daily:} 段一致的行为）。 */
    static PeriodSettings daily() {
        PeriodSettings settings = new PeriodSettings();
        settings.enabled = true;
        settings.amount = 3;
        settings.refreshCost = 1000.0;
        settings.refreshLimit = 3;
        return settings;
    }

    /** 内置默认：每周任务（默认关闭：开了才会凭空多出一批任务）。 */
    static PeriodSettings weekly() {
        PeriodSettings settings = new PeriodSettings();
        settings.enabled = false;
        settings.amount = 2;
        settings.resetWeekday = "MONDAY";
        settings.refreshCost = 2000.0;
        settings.refreshLimit = 1;
        return settings;
    }

    /** 内置默认：每月任务（默认关闭）。 */
    static PeriodSettings monthly() {
        PeriodSettings settings = new PeriodSettings();
        settings.enabled = false;
        settings.amount = 2;
        settings.resetMonthDay = 1;
        settings.refreshCost = 5000.0;
        settings.refreshLimit = 1;
        return settings;
    }

    /** 内置默认：自定义周期（默认关闭，周期长度 3 天）。 */
    static PeriodSettings custom() {
        PeriodSettings settings = new PeriodSettings();
        settings.enabled = false;
        settings.amount = 2;
        settings.period = "3d";
        settings.refreshCost = 0;
        settings.refreshLimit = 0;
        return settings;
    }

    /** 某种周期的内置默认值（配置里缺这一段时用它兜底）。 */
    public static PeriodSettings defaultsFor(QuestType type) {
        return switch (type) {
            case DAILY -> daily();
            case WEEKLY -> weekly();
            case MONTHLY -> monthly();
            case CUSTOM -> custom();
            case NORMAL -> new PeriodSettings();
        };
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 每位玩家每周期抽取的数量，至少 1。 */
    public int amount() {
        return Math.max(1, amount);
    }

    /** 重置时刻（0-23）：早于它算上一个周期。 */
    public int resetHour() {
        return Math.min(23, Math.max(0, resetHour));
    }

    /** 每周的重置星期几（1=周一 … 7=周日）；写错时退回周一并保持可用。 */
    public int resetWeekday() {
        if (resetWeekday == null || resetWeekday.isBlank()) {
            return 1;
        }
        try {
            return java.time.DayOfWeek.valueOf(resetWeekday.trim().toUpperCase(Locale.ROOT)).getValue();
        } catch (IllegalArgumentException e) {
            return 1;
        }
    }

    /** 每月的重置日期（1-28）：超过 28 会在部分月份不存在，因此夹紧到 28。 */
    public int resetMonthDay() {
        return Math.min(28, Math.max(1, resetMonthDay));
    }

    /** 自定义周期的长度描述，如 {@code 3d} / {@code 12h}。 */
    public String period() {
        return period == null || period.isBlank() ? "3d" : period.trim();
    }

    public double refreshCost() {
        return Math.max(0.0, refreshCost);
    }

    public int refreshLimit() {
        return Math.max(0, refreshLimit);
    }

    /** 配置的任务池；空表示「取该类型的全部任务」。 */
    public List<String> pool() {
        return pool == null ? List.of() : pool;
    }
}
