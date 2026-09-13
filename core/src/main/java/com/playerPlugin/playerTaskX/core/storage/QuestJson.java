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
        json.put("prerequisites", quest.prerequisites());
        json.put("refreshCost", quest.refreshCost());
        json.put("enabled", quest.enabled());
        json.put("objectives", quest.objectives().stream().map(QuestJson::objectiveToJson).toList());
        json.put("rewards", quest.rewards().stream().map(QuestJson::rewardToJson).toList());
        return json;
    }

    public static Map<String, Object> objectiveToJson(QuestObjective objective) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", objective.type());
        json.put("properties", objective.properties());
        return json;
    }

    public static Map<String, Object> rewardToJson(QuestReward reward) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", reward.type());
        json.put("properties", reward.properties());
        return json;
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

        List<String> prerequisites = new ArrayList<>();
        Object rawPrerequisites = json.get("prerequisites");
        if (rawPrerequisites instanceof List<?> list) {
            // 只收字符串，且非空：编辑器一侧的空行/占位不该变成一条「前置任务不存在」
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String prerequisiteId = String.valueOf(item).trim();
                if (!prerequisiteId.isEmpty()) {
                    prerequisites.add(prerequisiteId);
                }
            }
        }

        List<QuestObjective> objectives = new ArrayList<>();
        Object rawObjectives = json.get("objectives");
        if (rawObjectives instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> node = JsonCodec.asMap(item);
                objectives.add(QuestObjective.of(string(node.get("type"), ""), JsonCodec.asMap(node.get("properties"))));
            }
        }

        List<QuestReward> rewards = new ArrayList<>();
        Object rawRewards = json.get("rewards");
        if (rawRewards instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> node = JsonCodec.asMap(item);
                rewards.add(QuestReward.of(string(node.get("type"), ""), JsonCodec.asMap(node.get("properties"))));
            }
        }

        return new Quest(id, name, description, icon, category, type, prerequisites,
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
