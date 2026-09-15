package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.core.storage.codec.JsonCodec;
import com.playerPlugin.playerTaskX.core.storage.codec.PresetJson;
import com.playerPlugin.playerTaskX.core.storage.codec.QuestJson;

/**
 * {@code quests/} 与 {@code presets/} 两个只读 YAML 目录的来源构造：字段名与形状由 {@link QuestJson} / {@link PresetJson} 定义，本类只把文档交给它们。
 * 目录是给人写的——插件只在空着时铺一次示例，之后从不写入；改这些文件要重启或 {@code /ptxa reload} 才生效。
 */
public final class YamlDefinitions {

    private YamlDefinitions() {
    }

    /** {@code quests/} 目录的只读来源。 */
    public static YamlSources<Quest> questSources(DefinitionFolder folder) {
        return new YamlSources<>(folder, YamlDefinitions::quest, Quest::id);
    }

    /** {@code presets/} 目录的只读来源。 */
    public static YamlSources<Preset> presetSources(DefinitionFolder folder) {
        return new YamlSources<>(folder, YamlDefinitions::preset, Preset::id);
    }

    private static Quest quest(DefinitionFolder.Document document) {
        return QuestJson.fromJson(document.values());
    }

    /** 预设文档 → 模型：类别先看 {@code kind}、再看父目录名，都判断不出来就抛异常（猜错比少加载更难发现）。 */
    private static Preset preset(DefinitionFolder.Document document) {
        String kind = JsonCodec.text(document.values().get("kind")).trim();
        if (kind.isBlank()) {
            kind = kindFromFolder(document.parentName());
        }
        if (kind == null) {
            throw new IllegalArgumentException("缺少 kind（objectives 或 rewards），也无法从目录名判断类别");
        }
        Preset preset = PresetJson.fromJson(kind, document.values());
        if (preset == null) {
            throw new IllegalArgumentException("预设缺少 type（或 id 为空）");
        }
        return preset;
    }

    private static String kindFromFolder(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return null;
        }
        String normalized = folderName.trim().toLowerCase(java.util.Locale.ROOT);
        return Preset.OBJECTIVES.equals(normalized) || Preset.REWARDS.equals(normalized) ? normalized : null;
    }
}
