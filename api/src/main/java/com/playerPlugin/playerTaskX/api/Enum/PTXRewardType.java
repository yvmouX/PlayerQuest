package com.playerPlugin.playerTaskX.api.Enum;

import java.util.Locale;

public enum PTXRewardType {
    COMMAND,
    ITEM,
    MONEY,
    EXP,
    NONE;

    public static PTXRewardType fromString(String value) {
        if (value == null) return NONE;
        try {
            return PTXRewardType.valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    public static boolean isValid(PTXRewardType type) {
        return type != null && type != NONE;
    }
}