package com.playerPlugin.playerTaskX.PlayerTask.Enum;

import java.util.Locale;

public enum PTXTaskType {
    CYCLE,
    FOREVER,
    LIMIT,
    NONE;

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
