package com.playerPlugin.playerTaskX.core.storage.jdbc;

import java.util.Locale;

/**
 * 枚举列的容错解析：缺失或非法一律退回给定默认值并记告警；存储层容错优先，一条脏数据不该让整份任务列表抛异常。
 * 告警出口只有这一处，保证「库里有一条脏数据」这类消息的措辞与去向一致。
 */
final class EnumText {

    private EnumText() {
    }

    /** 解析枚举名（大小写不敏感、去空白）；空值或非法值返回 {@code fallback} 并记一次告警。 */
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
