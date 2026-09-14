package com.playerPlugin.playerTaskX.core.quest;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 预设引用（{@code objectives: [{preset: mine-stone}]}）的展开与校验。
 *
 * <h2>为什么展开要单独一步</h2>
 * 定义里存的是<b>作者写的那份</b>（此刻就是一个 {@code preset} 引用），引擎要的是<b>生效值</b>。
 * 展开只在两个时刻发生——载入定义时与保存定义时——都在 {@link QuestAdminService} 里，
 * 因此「哪些字段最终生效」只有一处结论，编辑器的预览、GUI、占位符看到的都一致。
 *
 * <h2>引用不带覆盖项</h2>
 * 引用就是「这条配置完全由预设提供」：字段不写、也不认。曾经允许 {@code preset} 与
 * {@code properties} 并存（覆盖项赢过预设），代价是同一个字段有两个来源——
 * 「这个任务实际在做什么」要心算一遍预设 ⊕ 覆盖，而表单里既没法安全地编辑覆盖项，
 * 也没法把「哪些字段被覆盖了」说清楚。要偏离预设就走
 * 「展开为独立配置」：把当前生效值复制成一份独立配置，之后它与预设再无关系。
 * <p>
 * 因此条目上多写的字段是<b>错误</b>而不是覆盖：{@link #problems} 报出来，
 * {@link #trim} 在保存时清掉，库里最终只剩 {@code {preset: id}}。
 *
 * <h2>预设被删掉/改名了怎么办</h2>
 * 引用会变成悬空：展开时<b>不</b>静默丢掉这个目标（那会让任务悄悄少一个条件），
 * 而是保留引用、把类型留空，并报一条校验问题——
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

    /**
     * 校验任务里的预设引用，返回问题清单（空表示没问题）。
     * <p>
     * 三类问题都会让目标/奖励<b>实际不生效</b>，但表面上看任务还在，因此必须报出来：
     * 预设 id 写错、预设已删除、预设的类别不对（拿奖励预设当目标用），
     * 以及在引用上写了字段（那些字段不生效，多半来自「以为覆盖项还能用」或旧的配置文件）。
     */
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

    /**
     * 把引用瘦身成只剩 {@code preset} 键。
     * <p>
     * 引用条目上的其它键都不生效（字段全由预设提供），但 {@code quests/*.yml} 里可能还留着
     * 早期「引用 + 覆盖项」写法，手工改库也会留下它们。保存前统一清掉，库里只保留引用本身，
     * 与「引用就是引用」这件事保持一致；清理前 {@link #problems} 会先把它们报出来。
     */
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
