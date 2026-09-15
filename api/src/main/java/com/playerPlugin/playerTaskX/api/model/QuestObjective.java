package com.playerPlugin.playerTaskX.api.model;

import java.util.Map;

/**
 * 任务目标：一份配置数据，行为由 {@link com.playerPlugin.playerTaskX.api.objective.ObjectiveType} 提供。
 * 引用预设时 {@code properties} 是生效值（引擎按它判定）、{@code authored} 是作者写的那份（落库按它写回），缺一不可。
 */
public record QuestObjective(String type, Map<String, Object> properties, Map<String, Object> authored) {

    /** 预设引用的保留键：出现在 {@link #authored()} 里就表示这个目标引用了预设。 */
    public static final String PRESET_KEY = "preset";

    public QuestObjective {
        properties = ConfigMap.copy(properties);
        authored = authored == null ? properties : ConfigMap.copy(authored);
    }

    /** 直接写配置（不引用预设）的目标。 */
    public QuestObjective(String type, Map<String, Object> properties) {
        this(type, properties, properties);
    }

    public static QuestObjective of(String type, Map<String, Object> properties) {
        return new QuestObjective(type, properties);
    }

    /** 引用了哪个预设；没引用返回 {@code null}。 */
    public String presetId() {
        return ConfigMap.presetId(authored);
    }

    /** 取字符串配置，缺失返回默认值。 */
    public String string(String key, String fallback) {
        return ConfigMap.string(properties, key, fallback);
    }

    /** 取整数配置，缺失或非法返回默认值。 */
    public int integer(String key, int fallback) {
        return ConfigMap.integer(properties, key, fallback);
    }

    /** 目标所需数量，统一约定 {@code amount} 键，默认 1。 */
    public int amount() {
        return Math.max(1, integer("amount", 1));
    }
}
