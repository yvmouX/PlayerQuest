package com.playerPlugin.playerTaskX.PlayerTask.Enum;

import java.util.Locale;

public enum PTXActionType {
    CRAFT,
    KILL,
    BREAK,
    ENCHANT,
    FISHING,
    SCISSOR,
    BREED,
    TAME,
    CONSUME,
    TRIGGER,
    DROP,
    TAKE,
    PLACE,
    NONE;

    private static boolean isValid(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            PTXActionType.valueOf(value.toUpperCase(Locale.ENGLISH));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static PTXActionType fromString(String value) {
        if (isValid(value)) {
            return PTXActionType.valueOf(value.toUpperCase(Locale.ENGLISH));
        }
        return PTXActionType.NONE;
    }
}
