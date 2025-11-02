package com.playerPlugin.playerTaskX.PlayerTask.Enum;

import java.util.Locale;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;

public enum TaskActions {
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

    public static boolean isValid(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            TaskActions.valueOf(value.toUpperCase(Locale.ENGLISH));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static TaskActions fromString(String value) {
        if (isValid(value)) {
            return TaskActions.valueOf(value.toUpperCase(Locale.ENGLISH));
        }
        return TaskActions.NONE;
    }
}
