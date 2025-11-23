package com.playerPlugin.core.common.Enum;

import java.util.Locale;

public enum PTXTaskType {
    CYCLE, // 循环任务
    FOREVER, // 永久任务
    LIMIT, // 限时任务
    NONE; // 无效

    private static boolean isValid(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            PTXTaskType.valueOf(value.toUpperCase(Locale.ENGLISH));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static PTXTaskType fromString(String value) {
        if (isValid(value)) {
            return PTXTaskType.valueOf(value.toUpperCase(Locale.ENGLISH));
        }
        return PTXTaskType.NONE;
    }
}
