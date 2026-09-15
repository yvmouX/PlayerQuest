package com.playerPlugin.playerTaskX.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 目标与奖励共用的「配置表」读取工具。
 *
 * <h2>为什么两者共用</h2>
 * {@link QuestObjective} 与 {@link QuestReward} 只是同一份「类型 + 两份配置」的形状，
 * 取值语义（缺省、宽松转整数、预设引用键）逐字相同。各写一份的代价是「宽松到什么程度」
 * 会在两侧漂移——例如一边把 {@code "5.0"} 认成 5、另一边返回默认值，
 * 而表现只是某个字段悄悄失效。
 */
final class ConfigMap {

    private ConfigMap() {
    }

    /**
     * 复制成不可变的有序表。
     * <p>
     * 不可变是刻意的：模型是 record，外部拿到的表若能改，
     * {@code properties} 与 {@code authored} 就会在保存后悄悄不一致。
     */
    static Map<String, Object> copy(Map<String, Object> source) {
        return source == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    /** 引用了哪个预设；没引用（或键为空）返回 {@code null}。 */
    static String presetId(Map<String, Object> authored) {
        Object value = authored.get(QuestObjective.PRESET_KEY);
        if (value == null) {
            return null;
        }
        String id = String.valueOf(value).trim();
        return id.isEmpty() ? null : id;
    }

    /** 取字符串配置，缺失返回默认值。 */
    static String string(Map<String, Object> properties, String key, String fallback) {
        Object value = properties.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    /** 取整数配置，缺失或非法返回默认值。 */
    static int integer(Map<String, Object> properties, String key, int fallback) {
        Object value = properties.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
