package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.core.storage.JsonCodec;
import com.playerPlugin.playerTaskX.core.storage.QuestJson;
import com.playerPlugin.playerTaskX.core.web.PresetJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务 / 预设 ⇄ YAML 文档的映射，以及导出、导入与只读来源的构造。
 *
 * <h2>只有一份字段定义</h2>
 * 字段名与形状直接取自编辑器 JSON 契约（{@link QuestJson} / {@link PresetJson}），
 * 本类只负责「写哪些键、按什么顺序、省略哪些空值」。因此
 * {@code quests/x.yml}、编辑器导出的清单、网页编辑器的 YAML 视图三者可以互相粘贴，
 * 新增目标类型时这里一行都不用改。
 *
 * <h2>省略空的可选字段</h2>
 * 空字符串与空列表不写出去：手写文件里堆一串 {@code category: ''}、
 * {@code prerequisites: []} 只会让人以为必须填。省掉它们不丢信息——读的一侧本来就是
 * 「缺省即默认」。
 *
 * <p>键序与前端 YAML 视图保持一致（id、name、description、icon、category、type、
 * refreshCost、enabled、prerequisites、objectives、rewards），这样两边生成的文本可以逐行对照。
 */
public final class YamlDefinitions {

    private YamlDefinitions() {
    }

    // ------------------------------------------------------------------
    // 只读来源
    // ------------------------------------------------------------------

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

    /**
     * 预设文档 → 模型。
     * <p>
     * 类别先看内容里的 {@code kind}，再看直接父目录名（{@code presets/rewards/exp.yml}）；
     * 都判断不出来就抛异常，由 {@link YamlSources} 记一条告警并跳过——
     * 猜错会把奖励预设塞进目标列表，比少加载一条更难发现。
     */
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
            throw new IllegalArgumentException("缺少 type");
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

    // ------------------------------------------------------------------
    // 文档（导出 / 示例文件）
    // ------------------------------------------------------------------

    /** 任务 → 文档（键序即写进 YAML 的顺序）。 */
    public static Map<String, Object> questDocument(Quest quest) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", quest.id());
        document.put("name", quest.name());
        if (!quest.description().isEmpty()) {
            document.put("description", new ArrayList<>(quest.description()));
        }
        document.put("icon", quest.icon());
        if (!JsonCodec.text(quest.category()).isBlank()) {
            document.put("category", quest.category());
        }
        document.put("type", quest.type().name());
        document.put("refreshCost", YamlText.number(quest.refreshCost()));
        document.put("enabled", quest.enabled());
        if (!quest.prerequisites().isEmpty()) {
            document.put("prerequisites", new ArrayList<>(quest.prerequisites()));
        }
        document.put("objectives", quest.objectives().stream().map(YamlDefinitions::objectiveDocument).toList());
        document.put("rewards", quest.rewards().stream().map(YamlDefinitions::rewardDocument).toList());
        return document;
    }

    /** 预设 → 文档；{@code kind} 总是写出来，导出的清单才能被逐条拆成文件。 */
    public static Map<String, Object> presetDocument(Preset preset) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("kind", preset.kind());
        document.put("id", preset.id());
        document.put("name", preset.name());
        document.put("type", preset.type());
        if (!JsonCodec.text(preset.description()).isBlank()) {
            document.put("description", preset.description());
        }
        document.put("properties", YamlText.sortedProperties(preset.properties()));
        return document;
    }

    private static Map<String, Object> objectiveDocument(QuestObjective objective) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("type", objective.type());
        document.put("properties", YamlText.sortedProperties(objective.properties()));
        return document;
    }

    private static Map<String, Object> rewardDocument(QuestReward reward) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("type", reward.type());
        document.put("properties", YamlText.sortedProperties(reward.properties()));
        return document;
    }

    // ------------------------------------------------------------------
    // 导出 / 导入
    // ------------------------------------------------------------------

    /** 任务清单 → YAML 文本（顶层是列表）。 */
    public static String writeQuests(List<Quest> quests) {
        List<Map<String, Object>> documents = new ArrayList<>(quests.size());
        for (Quest quest : quests) {
            documents.add(questDocument(quest));
        }
        return YamlText.write(documents);
    }

    /** 预设清单 → YAML 文本（顶层是列表，每项带 {@code kind}）。 */
    public static String writePresets(List<Preset> presets) {
        List<Map<String, Object>> documents = new ArrayList<>(presets.size());
        for (Preset preset : presets) {
            documents.add(presetDocument(preset));
        }
        return YamlText.write(documents);
    }

    /**
     * YAML 文本 → 任务清单（含缺 id 的条目）。
     * <p>
     * 接受三种形状（列表 / {@code quests:} 包一层 / 单个定义）。缺 id 的条目<b>不在这里丢掉</b>：
     * 调用方（导入接口）要把它列进「跳过了哪些」，否则用户会以为自己全导进去了。
     */
    public static List<Quest> readQuests(String yaml) {
        List<Quest> quests = new ArrayList<>();
        for (Map<String, Object> document : YamlText.readDocuments(yaml, "quests")) {
            quests.add(QuestJson.fromJson(document));
        }
        return quests;
    }

    /** YAML 文本 → 预设清单；缺 {@code kind} 时按 {@code defaultKind} 处理（导入界面已按类别分组）。 */
    public static List<Preset> readPresets(String yaml, String defaultKind) {
        List<Preset> presets = new ArrayList<>();
        for (Map<String, Object> document : YamlText.readDocuments(yaml, "presets")) {
            Map<String, Object> copy = new LinkedHashMap<>(document);
            if (JsonCodec.text(copy.get("kind")).isBlank()) {
                copy.put("kind", defaultKind);
            }
            Preset preset = PresetJson.fromJson(JsonCodec.text(copy.get("kind")), copy);
            if (preset != null) {
                presets.add(preset);
            }
        }
        return presets;
    }
}
