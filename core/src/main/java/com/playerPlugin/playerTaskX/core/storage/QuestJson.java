package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务 ↔ 网页编辑器 JSON 的映射。
 * <p>
 * 单独一层而不是直接序列化模型：模型是 record（不可变、构造器即契约），
 * 而编辑器需要「字段齐全、缺省友好」的对象；混在一起会让模型被迫迁就前端。
 */
public final class QuestJson {

    private QuestJson() {
    }

    /** 任务 → JSON 对象。 */
    public static Map<String, Object> toJson(Quest quest) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", quest.id());
        json.put("name", quest.name());
        json.put("description", quest.description());
        json.put("icon", quest.icon());
        json.put("category", quest.category());
        json.put("type", quest.type().name());
        json.put("refreshCost", quest.refreshCost());
        json.put("enabled", quest.enabled());
        json.put("objectives", quest.objectives().stream().map(QuestJson::objectiveToJson).toList());
        json.put("rewards", quest.rewards().stream().map(QuestJson::rewardToJson).toList());
        return json;
    }

    public static Map<String, Object> objectiveToJson(QuestObjective objective) {
        return nodeToJson(objective.type(), objective.properties(), objective.authored(), objective.presetId());
    }

    public static Map<String, Object> rewardToJson(QuestReward reward) {
        return nodeToJson(reward.type(), reward.properties(), reward.authored(), reward.presetId());
    }

    /**
     * 目标 / 奖励 → JSON 节点。
     * <p>
     * 引用预设时给两样东西：{@code preset}（引用本身）与 {@code resolved}（预设给的生效值，
     * 供界面直接显示）。不给 {@code properties}：引用条目没有「任务自己写的字段」，
     * 送一个空表过去只会让人以为那里可以填东西（要调值先「展开为独立配置」）。
     */
    private static Map<String, Object> nodeToJson(String type, Map<String, Object> effective,
                                                  Map<String, Object> authored, String presetId) {
        Map<String, Object> json = new LinkedHashMap<>();
        if (presetId != null) {
            json.put(QuestObjective.PRESET_KEY, presetId);
            json.put("type", type);
            json.put("resolved", effective);
            return json;
        }
        json.put("type", type);
        json.put("properties", effective);
        return json;
    }

    /**
     * JSON 节点 → 目标 / 奖励的公共部分。
     * <p>
     * 这里<b>不</b>展开预设（拿不到预设仓储）：引用条目的生效值由 {@code PresetRefs.resolve}
     * 在载入/保存时统一展开。{@code authored} 记下作者写的那份——引用时就是 {@code {preset: id}}，
     * 这样「引用」不会在编辑器往返里丢掉；引用条目上多写的字段会被保留下来交给校验报错
     * （{@code PresetRefs.problems}），保存时由 {@code trim} 清掉，不在这里静默丢弃。
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
     * 容错优先：编辑器传来的字段可能缺失或类型不符，这里一律降级而不是抛异常——
     * 前端一次误操作不应该让整个保存请求 500。
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
