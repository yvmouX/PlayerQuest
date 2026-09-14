package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.Preset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预设 ⇄ 文档（普通 {@code Map}）的映射：网页编辑器的预设 JSON 与 {@code presets/*.yml} 共用这一份字段定义。
 *
 * <h2>为什么不直接把 {@link Preset} 序列化出去</h2>
 * 模型是 record（不可变、构造器即契约），而编辑器需要「字段齐全、缺省友好」的对象，
 * 混在一起会让模型被迫迁就前端。另外已发布的前端依赖既有形状——按类别分组的
 * {@code {objectives:[...], rewards:[...]}} 与保存时的单项回显 {@code {ok, preset}}，
 * 有这一层薄映射，存储实现怎么换都不会牵动前端。
 *
 * <h2>为什么它在 storage 而不是 web</h2>
 * 两个调用方各在一侧：web 的 {@code EditorApi} 用它处理预设接口，storage 的
 * {@link com.playerPlugin.playerTaskX.core.storage.yaml.YamlDefinitions} 用它读 {@code presets/*.yml}。
 * 放在 web 里会让存储层反过来依赖 web 包，「删掉网页编辑器」就删不干净；
 * 与同为「文档映射」的 {@link QuestJson} 并排放在这里，两者的角色也一致。
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
     * 编辑器请求体（或 YAML 文件的内容）→ 预设。
     *
     * @param kind 类别，来自 URL 路径（{@code objectives} / {@code rewards}）或文件里的 {@code kind}
     * @return 预设；缺少 {@code type} 时返回 null（HTTP 侧回 400，文件侧跳过并告警）
     */
    public static Preset fromJson(String kind, Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        String type = JsonCodec.text(body.get("type"));
        if (type.isBlank()) {
            return null;
        }
        String id = JsonCodec.text(body.get("id"));
        if (id.isBlank()) {
            // 编辑器未指定 id 时生成一个：预设必须可被删除与覆盖
            id = Preset.normalizeKind(kind) + "-" + Long.toHexString(System.nanoTime() & 0xFFFFFFL);
        }
        String name = JsonCodec.text(body.get("name"));
        return new Preset(Preset.normalizeKind(kind), id,
                name.isBlank() ? type : name,
                type, JsonCodec.asMap(body.get("properties")), JsonCodec.text(body.get("description")));
    }
}
