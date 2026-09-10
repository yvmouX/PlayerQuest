package com.playerPlugin.playerTaskX.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任务奖励：一份「配置数据」，发放行为由
 * {@link com.playerPlugin.playerTaskX.api.reward.RewardType} 提供。
 *
 * @param type       奖励类型 id，如 {@code money}
 * @param properties 该类型的配置（键由类型 schema 定义）
 */
public record QuestReward(String type, Map<String, Object> properties) {

    public QuestReward {
        properties = properties == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static QuestReward of(String type, Map<String, Object> properties) {
        return new QuestReward(type, properties);
    }

    public String string(String key, String fallback) {
        Object value = properties.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

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
