package com.playerPlugin.playerTaskX.api.model;

import java.util.Map;

/**
 * 任务奖励：一份「配置数据」，发放行为由
 * {@link com.playerPlugin.playerTaskX.api.reward.RewardType} 提供。
 *
 * <p>与 {@link QuestObjective} 一样带两份配置：{@code properties} 是生效值（引用预设时就是
 * 预设给的值），{@code authored} 是作者写的那份（引用时只有 {@code preset} 键），
 * 落库与导出按后者写回。
 *
 * @param type       奖励类型 id，如 {@code money}
 * @param properties 生效配置
 * @param authored   作者写的配置（含 {@code preset} 键；未引用预设时等于 {@code properties}）
 */
public record QuestReward(String type, Map<String, Object> properties, Map<String, Object> authored) {

    public QuestReward {
        properties = ConfigMap.copy(properties);
        authored = authored == null ? properties : ConfigMap.copy(authored);
    }

    /** 直接写配置（不引用预设）的奖励。 */
    public QuestReward(String type, Map<String, Object> properties) {
        this(type, properties, properties);
    }

    public static QuestReward of(String type, Map<String, Object> properties) {
        return new QuestReward(type, properties);
    }

    /** 引用了哪个预设；没引用返回 {@code null}。 */
    public String presetId() {
        return ConfigMap.presetId(authored);
    }

    public String string(String key, String fallback) {
        return ConfigMap.string(properties, key, fallback);
    }

    public int integer(String key, int fallback) {
        return ConfigMap.integer(properties, key, fallback);
    }

    public double decimal(String key, double fallback) {
        Object value = properties.get(key);
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
