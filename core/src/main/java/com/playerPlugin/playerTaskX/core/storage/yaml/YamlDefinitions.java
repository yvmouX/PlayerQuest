package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.core.storage.JsonCodec;
import com.playerPlugin.playerTaskX.core.storage.PresetJson;
import com.playerPlugin.playerTaskX.core.storage.QuestJson;

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
 * 空字符串与空列表不写出去：手写文件里堆一串 {@code category: ''} 只会让人以为必须填。
 * 省掉它们不丢信息——读的一侧本来就是「缺省即默认」。
 *
 * <p>键序与前端 YAML 视图保持一致（id、name、description、icon、category、type、
 * refreshCost、enabled、objectives、rewards），这样两边生成的文本可以逐行对照。
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

    /**
     * 目标 → YAML 节点。
     * <p>
     * 引用预设时<b>只</b>写 {@code preset: <id>}：类型与字段都由预设提供，写进文件只会是一份
     * 会过期的副本（改预设后文件里的值就是错的）。要偏离预设，先在编辑器里「展开为独立配置」。
     */
    private static Map<String, Object> objectiveDocument(QuestObjective objective) {
        return nodeDocument(objective.type(), objective.properties(), objective.presetId());
    }

    private static Map<String, Object> rewardDocument(QuestReward reward) {
        return nodeDocument(reward.type(), reward.properties(), reward.presetId());
    }

    private static Map<String, Object> nodeDocument(String type, Map<String, Object> effective, String presetId) {
        Map<String, Object> document = new LinkedHashMap<>();
        if (presetId != null) {
            document.put(QuestObjective.PRESET_KEY, presetId);
            return document;
        }
        document.put("type", type);
        document.put("properties", YamlText.sortedProperties(effective));
        return document;
    }

    // ------------------------------------------------------------------
    // 导出 / 导入
    // ------------------------------------------------------------------

    /**
     * 单个任务 → YAML 文本（顶层是「键: 值」，也就是 {@code quests/<id>.yml} 的形状）。
     * <p>
     * <b>保留 {@code id} 字段</b>：导入接口拿到的是文本、看不到文件名，
     * 靠它才知道要覆盖哪一条；文件名与它一致时也不冲突（文件名只是默认值）。
     */
    public static String writeQuest(Quest quest) {
        return YamlText.write(questDocument(quest));
    }

    /** 单个预设 → YAML 文本（每项带 {@code kind}，否则只能靠目录名判断类别）。 */
    public static String writePreset(Preset preset) {
        return YamlText.write(presetDocument(preset));
    }

    /**
     * 单任务 YAML 文本 → 任务。
     * <p>
     * 只接受「一个文件一个任务」：顶层是列表时明确报错并指出该用 zip。
     * 宽松地接受列表会让「导入了一个文件却多出十几条任务」变成静默行为。
     */
    public static Quest readQuest(String yaml) {
        return QuestJson.fromJson(singleDocument(yaml, "任务", "quests"));
    }

    /** 单预设 YAML 文本 → 预设；缺 {@code kind} 时按 {@code defaultKind} 处理。 */
    public static Preset readPreset(String yaml, String defaultKind) {
        Map<String, Object> document = new LinkedHashMap<>(singleDocument(yaml, "预设", "presets"));
        if (JsonCodec.text(document.get("kind")).isBlank()) {
            document.put("kind", defaultKind);
        }
        Preset preset = PresetJson.fromJson(JsonCodec.text(document.get("kind")), document);
        if (preset == null) {
            throw new IllegalArgumentException("预设缺少 type");
        }
        return preset;
    }

    /**
     * 顶层必须是一个映射（一个定义）。列表、标量、空文件都报错。
     *
     * @param label  报错措辞里的名字：任务 / 预设
     * @param folder 该放哪个目录（报错时给出出路）
     */
    private static Map<String, Object> singleDocument(String yaml, String label, String folder) {
        Map<String, Object> document = YamlText.readMap(yaml);
        if (document == null || document.isEmpty()) {
            throw new IllegalArgumentException("一个文件只能放一个" + label + "（顶层是「键: 值」）；"
                    + "多个" + label + "请打包成 zip（每个文件一个），或把文件放进 " + folder + "/ 目录");
        }
        return document;
    }
}
