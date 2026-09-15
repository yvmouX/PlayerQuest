package com.playerPlugin.playerTaskX.core.storage.codec;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档（普通 {@code Map}）→ 任务的映射：{@code quests/*.yml} 与数据库的 {@code properties} 列共用这一份字段定义。
 * 只有「读」这一个方向：任务定义只由命令或人写的 YAML 文件产生，插件自己不生成文档。
 */
public final class QuestJson {

    private QuestJson() {
    }

    /**
     * 节点的 properties 记成 {@code authored}：本方法不展开预设（拿不到预设仓储），生效值由 {@code PresetRefs.resolve} 在载入/保存时展开；作者写的那份（引用时就是 {@code {preset: id}}）必须原样留下，引用才不会在文档往返里丢掉。
     */
    private static Map<String, Object> authoredOf(Map<String, Object> node) {
        Map<String, Object> properties = JsonCodec.asMap(node.get("properties"));
        String presetId = value(node.get(QuestObjective.PRESET_KEY));
        if (presetId == null || presetId.isBlank()) {
            return properties;
        }
        Map<String, Object> authored = new LinkedHashMap<>();
        authored.put(QuestObjective.PRESET_KEY, presetId.trim());
        authored.putAll(properties);
        return authored;
    }

    private static String value(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    /**
     * JSON → 任务。
     * <p>
     * 容错优先：文档里的字段可能缺失或类型不符（手写 YAML、手改库），这里一律降级而不是抛异常——
     * 一份写歪的定义不应该让整个文件或整个任务列表读不出来。
     */
    public static Quest fromJson(Map<String, Object> json) {
        String id = string(json.get("id"), "");
        String name = string(json.get("name"), id);
        String icon = string(json.get("icon"), "PAPER");
        String category = string(json.get("category"), "");
        com.playerPlugin.playerTaskX.api.model.QuestType type;
        try {
            type = com.playerPlugin.playerTaskX.api.model.QuestType.valueOf(
                    string(json.get("type"), "NORMAL").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            type = com.playerPlugin.playerTaskX.api.model.QuestType.NORMAL;
        }
        double refreshCost = decimal(json.get("refreshCost"), 0.0);
        boolean enabled = bool(json.get("enabled"), true);

        List<String> description = new ArrayList<>();
        Object rawDescription = json.get("description");
        if (rawDescription instanceof List<?> list) {
            list.forEach(line -> description.add(String.valueOf(line)));
        } else if (rawDescription instanceof String text && !text.isBlank()) {
            description.addAll(List.of(text.split("\\r?\\n")));
        }

        List<QuestObjective> objectives = new ArrayList<>();
        Object rawObjectives = json.get("objectives");
        if (rawObjectives instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> node = JsonCodec.asMap(item);
                objectives.add(new QuestObjective(string(node.get("type"), ""),
                        JsonCodec.asMap(node.get("properties")), authoredOf(node)));
            }
        }

        List<QuestReward> rewards = new ArrayList<>();
        Object rawRewards = json.get("rewards");
        if (rawRewards instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> node = JsonCodec.asMap(item);
                rewards.add(new QuestReward(string(node.get("type"), ""),
                        JsonCodec.asMap(node.get("properties")), authoredOf(node)));
            }
        }

        return new Quest(id, name, description, icon, category, type,
                objectives, rewards, refreshCost, enabled);
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static double decimal(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return fallback;
    }
}
