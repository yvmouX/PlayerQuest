package com.playerPlugin.playerTaskX.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任务目标：一份「配置数据」，行为由 {@link com.playerPlugin.playerTaskX.api.objective.ObjectiveType} 提供。
 * <p>
 * 参数统一放 {@code properties} 而不是为每种目标建子类：配置、数据库、网页编辑器、GUI
 * 四处都只需要一份通用实现；每种目标用 {@code schema()} 自描述字段，表单可自动生成。
 *
 * @param type       目标类型 id，如 {@code break_block}
 * @param properties 该类型的配置（键由类型 schema 定义）
 */
public record QuestObjective(String type, Map<String, Object> properties) {

    public QuestObjective {
        properties = properties == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static QuestObjective of(String type, Map<String, Object> properties) {
        return new QuestObjective(type, properties);
    }

    /** 取字符串配置，缺失返回默认值。 */
    public String string(String key, String fallback) {
        Object value = properties.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    /** 取整数配置，缺失或非法返回默认值。 */
    public int integer(String key, int fallback) {
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

    /** 目标所需数量，统一约定 {@code amount} 键，默认 1。 */
    public int amount() {
        return Math.max(1, integer("amount", 1));
    }
}
