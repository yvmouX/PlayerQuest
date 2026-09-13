package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.api.model.Preset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预设 ↔ 网页编辑器 JSON 的映射。
 *
 * <h2>为什么不直接把 {@link Preset} 序列化出去</h2>
 * 编辑器（含已发布的前端）依赖既有契约：<b>按类别分组</b>的
 * {@code {objectives:[...], rewards:[...]}}，以及保存时的单项回显 {@code {ok, preset}}。
 * 引入 {@link PresetRepository} 时把接口形状换掉会连带改前端，而这次改动的目的是
 * 「换存储后端」，不该顺手破坏前端契约。因此在这里做一层薄映射，
 * 让存储层可以自由换实现、编辑器侧接口保持不变。
 */
public final class PresetJson {

    private PresetJson() {
    }

    /** 全部预设 → 编辑器要的分组结构。 */
    public static Map<String, Object> toGrouped(List<Preset> presets) {
        List<Map<String, Object>> objectives = new ArrayList<>();
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (Preset preset : presets) {
            (preset.isReward() ? rewards : objectives).add(toJson(preset));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(Preset.OBJECTIVES, objectives);
        result.put(Preset.REWARDS, rewards);
        return result;
    }

    /** 单个预设 → 取景用的 JSON。 */
    public static Map<String, Object> toJson(Preset preset) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", preset.id());
        json.put("name", preset.name());
        json.put("type", preset.type());
        json.put("properties", preset.properties());
        json.put("description", preset.description());
        return json;
    }

    /**
     * 编辑器请求体 → 预设。
     *
     * @param kind 类别，来自 URL 路径（{@code objectives} / {@code rewards}）
     * @return 预设；缺少 {@code type} 时返回 null（调用方回 400）
     */
    public static Preset fromJson(String kind, Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        String type = text(body.get("type"));
        if (type.isBlank()) {
            return null;
        }
        String id = text(body.get("id"));
        if (id.isBlank()) {
            // 编辑器未指定 id 时生成一个：预设必须可被删除与覆盖
            id = Preset.normalizeKind(kind) + "-" + Long.toHexString(System.nanoTime() & 0xFFFFFFL);
        }
        String name = text(body.get("name"));
        return new Preset(Preset.normalizeKind(kind), id,
                name.isBlank() ? type : name,
                type, asMap(body.get("properties")), text(body.get("description")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
