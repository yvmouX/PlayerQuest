package com.playerPlugin.playerTaskX.core.preset;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 预设引用（{@code {preset: id}}）的展开与校验：只在载入与保存定义时展开成生效值，因此「哪些字段最终生效」只有一处结论。
 * 引用不认覆盖字段——条目上多写的键是错误（{@link #problems} 报出、{@link #trim} 清掉）；预设被删时保留引用并报问题，不静默丢掉目标。
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
                quest.type(), objectives, rewards, quest.refreshCost(), quest.enabled());
    }

    private static QuestObjective resolve(QuestObjective objective, Function<String, Preset> presets) {
        String presetId = objective.presetId();
        if (presetId == null) {
            return objective;
        }
        Preset preset = presets.apply(presetId);
        // 悬空引用：类型留空、配置为空，让这个目标什么都不命中，并靠 problems 报出原因
        return new QuestObjective(
                preset == null ? "" : preset.type(),
                preset == null ? Map.of() : preset.properties(),
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
                preset == null ? Map.of() : preset.properties(),
                reward.authored());
    }

    /** 校验任务里的预设引用并返回问题清单（空表示没问题）：id 写错、预设被删、类别不对、引用上多写字段都会让目标/奖励实际不生效，必须报出来。 */
    public static List<String> problems(Quest quest, Function<String, Preset> presets) {
        List<String> problems = new ArrayList<>();
        if (quest == null) {
            return problems;
        }
        for (QuestObjective objective : quest.objectives()) {
            problems.addAll(problems(objective.presetId(), objective.authored(),
                    Preset.OBJECTIVES, "目标", presets));
        }
        for (QuestReward reward : quest.rewards()) {
            problems.addAll(problems(reward.presetId(), reward.authored(),
                    Preset.REWARDS, "奖励", presets));
        }
        return problems;
    }

    private static List<String> problems(String presetId, Map<String, Object> authored,
                                         String expectedKind, String label,
                                         Function<String, Preset> presets) {
        if (presetId == null) {
            return List.of();
        }
        List<String> problems = new ArrayList<>();
        Preset preset = presets.apply(presetId);
        if (preset == null) {
            problems.add("引用的预设 " + presetId + " 不存在（已删除或 id 写错），"
                    + "这个" + label + "当前不生效");
        } else if (!expectedKind.equals(preset.kind())) {
            problems.add("引用的预设 " + presetId + " 是" + kindLabel(preset.kind())
                    + "预设，不能用在" + label + "上");
        }
        List<String> extra = extraKeys(authored);
        if (!extra.isEmpty()) {
            problems.add("引用了预设 " + presetId + " 的" + label + "不能再写字段（"
                    + String.join("、", extra) + "）：字段由预设提供，这些值不生效；"
                    + "要单独调值请先「展开为独立配置」");
        }
        return problems;
    }

    /** 引用条目上除 {@code preset} 之外还写了哪些键（按写入顺序）。 */
    private static List<String> extraKeys(Map<String, Object> authored) {
        List<String> extra = new ArrayList<>();
        for (String key : authored.keySet()) {
            if (!QuestObjective.PRESET_KEY.equals(key)) {
                extra.add(key);
            }
        }
        return extra;
    }

    private static String kindLabel(String kind) {
        return Preset.REWARDS.equals(kind) ? "奖励" : "目标";
    }

    /** 把引用瘦身成只剩 {@code preset} 键：其余键都不生效（字段全由预设提供），保存前统一清掉，清理前由 {@link #problems} 先报出来。 */
    public static Quest trim(Quest quest) {
        if (quest == null) {
            return null;
        }
        List<QuestObjective> objectives = new ArrayList<>(quest.objectives().size());
        for (QuestObjective objective : quest.objectives()) {
            objectives.add(trim(objective));
        }
        List<QuestReward> rewards = new ArrayList<>(quest.rewards().size());
        for (QuestReward reward : quest.rewards()) {
            rewards.add(trim(reward));
        }
        return new Quest(quest.id(), quest.name(), quest.description(), quest.icon(), quest.category(),
                quest.type(), objectives, rewards, quest.refreshCost(), quest.enabled());
    }

    private static QuestObjective trim(QuestObjective objective) {
        String presetId = objective.presetId();
        if (presetId == null) {
            return objective;
        }
        return new QuestObjective(objective.type(), objective.properties(),
                Map.of(QuestObjective.PRESET_KEY, presetId));
    }

    private static QuestReward trim(QuestReward reward) {
        String presetId = reward.presetId();
        if (presetId == null) {
            return reward;
        }
        return new QuestReward(reward.type(), reward.properties(),
                Map.of(QuestObjective.PRESET_KEY, presetId));
    }
}
