package com.playerPlugin.playerTaskX.core.storage.jdbc;

import java.util.Locale;

/**
 * 枚举列的容错解析：缺失或非法一律退回给定默认值，并记一条告警。
 *
 * <p>存储层的容错优先级高于严格性：手工改过库、旧版本格式、写入中途崩溃都会留下
 * 对不上枚举名的值，而「读不出来」的表现是整个玩家的任务列表抛异常。
 * 因此这里不抛异常——宁可少一条字段的精度，也不能让一条脏数据拖垮整次读取。
 *
 * <p>告警出口只有这一处：这些消息都是「库里有一条脏数据」这一类，
 * 措辞与去向必须一致，否则同一种问题会在日志里出现两种说法。
 */
final class EnumText {

    private EnumText() {
    }

    /**
     * 解析枚举名（大小写不敏感、去空白）；空值或非法值返回 {@code fallback}。
     *
     * @param type     枚举类型
     * @param raw      列里的原文
     * @param fallback 空值或非法值时的兜底枚举值
     */
    static <E extends Enum<E>> E parse(Class<E> type, String raw, E fallback) {
        return parse(type, raw, fallback, type.getSimpleName());
    }

    /**
     * 带自定义名字的解析：告警里用「任务类型」「玩家任务的状态」这类说法，
     * 比裸的枚举类名更适合直接读日志的人。
     *
     * @param label 告警里的名字，如「任务类型」
     */
    static <E extends Enum<E>> E parse(Class<E> type, String raw, E fallback, String label) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn(label + "非法，已按 " + fallback.name() + " 处理: " + raw);
            return fallback;
        }
    }

    static void warn(String message) {
        System.err.println("[PlayerTaskX] " + message);
    }
}
