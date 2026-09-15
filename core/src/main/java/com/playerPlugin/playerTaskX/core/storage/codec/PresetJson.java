package com.playerPlugin.playerTaskX.core.storage.codec;

import com.playerPlugin.playerTaskX.api.model.Preset;

import java.util.Map;

/**
 * 预设文档（普通 {@code Map}）→ 预设的映射：数据库里的预设与 {@code presets/*.yml} 共用这一份字段定义。
 * 只有「读」这一个方向：预设只由人写的 YAML 文件产生，插件自己不生成文档。
 */
public final class PresetJson {

    private PresetJson() {
    }

    /**
     * {@code presets/*.yml} 的内容（或其它文档来源）→ 预设。
     *
     * @param kind 类别，来自文件里的 {@code kind}
     * @return 预设；缺少 {@code type} 或 {@code id} 时返回 null（调用方跳过并告警）
     */
    public static Preset fromJson(String kind, Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        String type = JsonCodec.text(body.get("type")).trim();
        if (type.isBlank()) {
            return null;
        }
        // id 由目录扫描注入（文件名即 id）；这里不再兜底生成一个——
        // 「没写 id 就随机造一个」是网页编辑器新建预设时的行为，现在没有这种入口了
        String id = JsonCodec.text(body.get("id")).trim();
        if (id.isBlank()) {
            return null;
        }
        return new Preset(Preset.normalizeKind(kind), id, type, JsonCodec.asMap(body.get("properties")));
    }
}
