package com.playerPlugin.playerTaskX.api.Enum;

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

    public static PTXActionType fromString(String value) {
        if (value == null) return NONE;
        try {
            return PTXActionType.valueOf(value.toUpperCase(Locale.ENGLISH));
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
    public static boolean isValid(PTXActionType type) {
        return type != null && type != NONE;
    }
}
