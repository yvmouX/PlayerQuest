package com.playerPlugin.playerTaskX.api.utils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TimeUtil {
    public static String getTime() {
        // 获取默认地域信息
        Locale defaultLocale = Locale.getDefault();
        // 获取系统默认时区
        ZoneId defaultZone = ZoneId.systemDefault();

        return ZonedDateTime.now()
                .format(DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withLocale(defaultLocale));
    }

    public static String formatTime(long timestamp) {
        Locale defaultLocale = Locale.getDefault();
        return java.time.Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withLocale(defaultLocale));
    }
}
