package com.playerPlugin.playerTaskX.core.quest;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.core.storage.JsonCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 预设引用（{@code objectives: [{preset: mine-stone, properties: {amount: 128}}]}）的展开与校验。
 *
 * <h2>为什么展开要单独一步</h2>
 * 定义里存的是<b>作者写的那份</b>（引用 + 覆盖项），引擎要的是<b>生效值</b>。
 * 展开只在两个时刻发生——载入定义时与保存定义时——都在 {@link QuestAdminService} 里，
 * 因此「哪些字段最终生效」只有一处结论，编辑器的预览、GUI、占位符看到的都一致。
 *
 * <h2>预设被删掉/改名了怎么办</h2>
 * 引用会变成悬空：展开时<b>不</b>静默丢掉这个目标（那会让任务悄悄少一个条件），
 * 而是保留作者写的覆盖项、把类型留空，并报一条校验问题——
 * 「任务为什么做不了」必须能在编辑器与 {@code /ptxa list} 里看到原因。
 */
public final class PresetRefs {

    private PresetRefs() {
    }

    /**
     * 展开任务里所有预设引用。
     *
     * @param presets 按 id 查预设；查不到表示引用悬空
     * @return 生效值已填好的任务；没有引用时原样返回
     */
    public static Quest resolve(Quest quest, Function<String, Preset> presets) {
        if (quest == null) {
            return null;
        }
        List<QuestObjective> objectives = new ArrayList<>(quest.objectives().size());
        for (QuestObjective objective : quest.objectives()) {
            objectives.add(resolve(objective, presets));
        }
        List<QuestReward> rewards = new ArrayList<>(quest.rewards().size());
        for (QuestReward reward : quest.rewards()) {
            rewards.add(resolve(reward, presets));
        }
        return new Quest(quest.id(), quest.name(), quest.description(), quest.icon(), quest.category(),
                quest.type(), quest.prerequisites(), objectives, rewards, quest.refreshCost(), quest.enabled());
    }

    private static QuestObjective resolve(QuestObjective objective, Function<String, Preset> presets) {
        String presetId = objective.presetId();
        if (presetId == null) {
            return objective;
        }
        Preset preset = presets.apply(presetId);
        return new QuestObjective(
                preset == null ? "" : preset.type(),
                merge(preset == null ? null : preset.properties(), objective.authored()),
                objective.authored());
    }

    private static QuestReward resolve(QuestReward reward, Function<String, Preset> presets) {
        String presetId = reward.presetId();
        if (presetId == null) {
            return reward;
        }
        Preset preset = presets.apply(presetId);
        return new QuestReward(
                preset == null ? "" : preset.type(),
                merge(preset == null ? null : preset.properties(), reward.authored()),
                reward.authored());
    }

    /**
     * 预设字段 ⊕ 任务自己写的覆盖项。
     * <p>
     * {@code preset} 键本身不进生效值：它是「这条配置从哪来」的记账，不是目标/奖励的字段，
     * 留在里面会让每种类型的 schema 校验都报一个「未知字段 preset」。
     */
    private static Map<String, Object> merge(Map<String, Object> presetProperties, Map<String, Object> authored) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (presetProperties != null) {
            merged.putAll(presetProperties);
        }
        for (Map.Entry<String, Object> entry : authored.entrySet()) {
            if (QuestObjective.PRESET_KEY.equals(entry.getKey())) {
                continue;
            }
            // 覆盖项值为 null 视为「不覆盖」：YAML 里写了个空键不该把预设的值抹掉
            if (entry.getValue() != null) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    /**
     * 校验任务里的预设引用，返回问题清单（空表示没问题）。
     * <p>
     * 三类问题都会让目标/奖励<b>实际不生效</b>，但表面上看任务还在，因此必须报出来：
     * 预设 id 写错、预设已删除、预设的类别不对（拿奖励预设当目标用）。
     */
    public static List<String> problems(Quest quest, Function<String, Preset> presets) {
        List<String> problems = new ArrayList<>();
        if (quest == null) {
            return problems;
        }
        for (QuestObjective objective : quest.objectives()) {
            problems.addAll(problems(objective.presetId(), Preset.OBJECTIVES, presets));
        }
        for (QuestReward reward : quest.rewards()) {
            problems.addAll(problems(reward.presetId(), Preset.REWARDS, presets));
        }
        return problems;
    }

    private static List<String> problems(String presetId, String expectedKind, Function<String, Preset> presets) {
        if (presetId == null) {
            return List.of();
        }
        Preset preset = presets.apply(presetId);
        if (preset == null) {
            return List.of("引用的预设 " + presetId + " 不存在（已删除或 id 写错），"
                    + "这个" + kindLabel(expectedKind) + "当前不生效");
        }
        if (!expectedKind.equals(preset.kind())) {
            return List.of("引用的预设 " + presetId + " 是" + kindLabel(preset.kind())
                    + "预设，不能用在" + kindLabel(expectedKind) + "上");
        }
        return List.of();
    }

    private static String kindLabel(String kind) {
        return Preset.REWARDS.equals(kind) ? "奖励" : "目标";
    }

    /**
     * 把生效值反推成「作者写的那份」：只保留与预设不同的字段。
     * <p>
     * 编辑器与导入接口可能送来已经展开过的配置（例如用户把 YAML 视图里的值改了），
     * 这时把继承来的值一起写回去，就等于把预设的值复制成了显式覆盖——
     * 之后改预设这些字段再也不会跟着变。因此保存前统一瘦身一次。
     */
    public static Quest trim(Quest quest, Function<String, Preset> presets) {
        if (quest == null) {
            return null;
        }
        List<QuestObjective> objectives = new ArrayList<>(quest.objectives().size());
        for (QuestObjective objective : quest.objectives()) {
            objectives.add(trim(objective, presets));
        }
        List<QuestReward> rewards = new ArrayList<>(quest.rewards().size());
        for (QuestReward reward : quest.rewards()) {
            rewards.add(trim(reward, presets));
        }
        return new Quest(quest.id(), quest.name(), quest.description(), quest.icon(), quest.category(),
                quest.type(), quest.prerequisites(), objectives, rewards, quest.refreshCost(), quest.enabled());
    }

    private static QuestObjective trim(QuestObjective objective, Function<String, Preset> presets) {
        String presetId = objective.presetId();
        if (presetId == null) {
            return objective;
        }
        Map<String, Object> authored = trim(presetId, objective.properties(), presets);
        return new QuestObjective(objective.type(), objective.properties(), authored);
    }

    private static QuestReward trim(QuestReward reward, Function<String, Preset> presets) {
        String presetId = reward.presetId();
        if (presetId == null) {
            return reward;
        }
        Map<String, Object> authored = trim(presetId, reward.properties(), presets);
        return new QuestReward(reward.type(), reward.properties(), authored);
    }

    private static Map<String, Object> trim(String presetId, Map<String, Object> effective,
                                            Function<String, Preset> presets) {
        Preset preset = presets.apply(presetId);
        Map<String, Object> presetProperties = preset == null ? Map.of() : preset.properties();
        Map<String, Object> authored = new LinkedHashMap<>();
        authored.put(QuestObjective.PRESET_KEY, presetId);
        for (Map.Entry<String, Object> entry : effective.entrySet()) {
            if (QuestObjective.PRESET_KEY.equals(entry.getKey())) {
                continue;
            }
            if (!presetProperties.containsKey(entry.getKey()) || !sameValue(presetProperties.get(entry.getKey()), entry.getValue())) {
                authored.put(entry.getKey(), entry.getValue());
            }
        }
        return authored;
    }

    /** 值比较放宽一点：YAML 读回来可能是 Integer，JSON 里可能是 Long/Double。 */
    private static boolean sameValue(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left instanceof Number a && right instanceof Number b) {
            return a.doubleValue() == b.doubleValue();
        }
        return JsonCodec.text(left).equals(JsonCodec.text(right));
    }
}
