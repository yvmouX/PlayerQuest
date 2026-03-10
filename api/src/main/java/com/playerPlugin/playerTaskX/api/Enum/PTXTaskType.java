package com.playerPlugin.playerTaskX.api.Enum;

import java.util.Locale;

/**
 * 1.
 * 循环任务CYCLE 执行完成后重置，可重复做
 * {@link PTXTaskStatus#NOT_STARTED} -> {@link PTXTaskStatus#IN_PROGRESS} -> {@link PTXTaskStatus#COMPLETED} -> {@link PTXTaskStatus#NOT_STARTED} -> ...
 * 2.
 * 定时任务TIMER 无论玩家当前状态如何，按时间间隔重置任务为{@link PTXTaskStatus#NOT_STARTED}
 * 3.
 * 永久任务FOREVER 一直存在，可随时做
 * {@link PTXTaskStatus#NOT_STARTED} -> {@link PTXTaskStatus#IN_PROGRESS} -> {@link PTXTaskStatus#COMPLETED}
 * 4.
 * 限时任务LIMIT 在指定时间内完成，超时未完成状态将更改为{@link PTXTaskStatus#EXPIRED}
 * 5.
 * 无效任务NONE 无效/未知类型（解析失败的默认值）
 */
public enum PTXTaskType {
    CYCLE,
    TIMER,
    FOREVER,
    LIMIT,
    NONE;

    public static PTXTaskType fromString(String value) {
        if (value == null) return NONE;
        try {
            return PTXTaskType.valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }


    /**
     * 不为空、或者NONE
     *
     * @param type 类型
     * @return boolean
     */
    public static boolean isValid(PTXTaskType type) {
        return type != null && type != NONE;
    }
}
