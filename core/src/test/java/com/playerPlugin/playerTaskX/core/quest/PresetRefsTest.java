package com.playerPlugin.playerTaskX.core.quest;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.storage.QuestJson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预设引用（{@code objectives: [{preset: mine-stone}]}）的测试。
 *
 * <p>要防的是几类静默错误：引用在保存一次之后退化成副本（此后改预设不再跟随）、
 * 引用悬空却被当成「这个目标不存在」悄悄丢掉、条目上多写的字段被当成覆盖项悄悄生效。</p>
 */
class PresetRefsTest {

    private final Map<String, Preset> presets = new LinkedHashMap<>();

    private final Function<String, Preset> lookup = id -> presets.get(id);

    @Test
    @DisplayName("展开：类型与字段全部来自预设，引用本身留着")
    void resolveTakesEverythingFromThePreset() {
        presets.put("mine-stone", new Preset(Preset.OBJECTIVES, "mine-stone", "挖石头", "break_block",
                map("target", "STONE", "amount", 64), ""));

        Quest quest = PresetRefs.resolve(quest(objective(map("preset", "mine-stone"))), lookup);

        QuestObjective objective = quest.objectives().get(0);
        assertEquals("break_block", objective.type(), "类型来自预设");
        assertEquals(map("target", "STONE", "amount", 64), objective.properties(), "生效值就是预设那份");
        assertFalse(objective.properties().containsKey("preset"), "preset 是记账键，不该进生效值");
        assertEquals("mine-stone", objective.presetId(), "引用本身要留着，否则下次保存就退化成副本");
        assertTrue(PresetRefs.problems(quest, lookup).isEmpty());
    }

    @Test
    @DisplayName("悬空引用：类型留空、配置为空，并报出「预设不存在」")
    void missingPresetIsReported() {
        Quest quest = PresetRefs.resolve(quest(objective(map("preset", "gone"))), lookup);

        assertEquals("", quest.objectives().get(0).type());
        assertTrue(quest.objectives().get(0).properties().isEmpty(), "查不到预设就没有生效值");
        List<String> problems = PresetRefs.problems(quest, lookup);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("gone"), problems.get(0));
        assertTrue(problems.get(0).contains("不存在"), problems.get(0));
    }

    @Test
    @DisplayName("引用上多写的字段不生效，并报出来（覆盖项机制已移除）")
    void extraFieldsOnReferenceAreReportedAndIgnored() {
        presets.put("mine-stone", new Preset(Preset.OBJECTIVES, "mine-stone", "挖石头", "break_block",
                map("target", "STONE", "amount", 64), ""));

        // 早期写法（preset + 覆盖项）与手工改库都可能长这样
        Quest quest = PresetRefs.resolve(
                quest(objective(map("preset", "mine-stone", "amount", 128))), lookup);

        assertEquals(64, quest.objectives().get(0).properties().get("amount"),
                "多写的 amount 不生效，生效值仍然是预设的");
        List<String> problems = PresetRefs.problems(quest, lookup);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("不能再写字段"), problems.get(0));
        assertTrue(problems.get(0).contains("amount"), problems.get(0));
        assertTrue(problems.get(0).contains("展开为独立配置"), problems.get(0));
    }

    @Test
    @DisplayName("trim：引用被清成只剩 preset 键")
    void trimLeavesOnlyTheReference() {
        presets.put("mine-stone", new Preset(Preset.OBJECTIVES, "mine-stone", "挖石头", "break_block",
                map("target", "STONE", "amount", 64), ""));
        Quest polluted = quest(new QuestObjective("break_block",
                map("target", "STONE", "amount", 64),
                map("preset", "mine-stone", "target", "STONE", "amount", 128)));

        Quest trimmed = PresetRefs.trim(polluted);
        assertEquals(map("preset", "mine-stone"), trimmed.objectives().get(0).authored());
        assertEquals("mine-stone", trimmed.objectives().get(0).presetId());
        // 幂等：已经干净的再清一次不变
        assertEquals(PresetRefs.trim(trimmed).objectives(), trimmed.objectives());
    }

    @Test
    @DisplayName("trim 不碰独立配置（没有引用的条目原样返回）")
    void trimIgnoresPlainDefinitions() {
        Quest quest = quest(objective(map("target", "STONE", "amount", 64)));

        Quest trimmed = PresetRefs.trim(quest);

        assertEquals(quest.objectives(), trimmed.objectives());
        assertNull(trimmed.objectives().get(0).presetId());
    }

    @Test
    @DisplayName("trim 后再展开，生效值与原来一致（保存 → 载入是一次恒等变换）")
    void trimThenResolveIsStable() {
        presets.put("mine-stone", new Preset(Preset.OBJECTIVES, "mine-stone", "挖石头", "break_block",
                map("target", "STONE", "amount", 64), ""));

        Quest effective = PresetRefs.resolve(
                quest(objective(map("preset", "mine-stone"))), lookup);
        Quest again = PresetRefs.resolve(PresetRefs.trim(effective), lookup);

        assertEquals(effective.objectives().get(0).properties(), again.objectives().get(0).properties());
        assertEquals("mine-stone", again.objectives().get(0).presetId());
    }

    @Test
    @DisplayName("类别用错：拿奖励预设当目标用会被报出来")
    void kindMismatchIsReported() {
        presets.put("reward-money", new Preset(Preset.REWARDS, "reward-money", "奖励金币", "money",
                map("amount", 100), ""));
        presets.put("obj-chat", new Preset(Preset.OBJECTIVES, "obj-chat", "发言", "chat",
                map("amount", 1), ""));

        Quest right = PresetRefs.resolve(quest(
                objective(map("preset", "obj-chat")),
                reward(map("preset", "reward-money"))), lookup);
        assertTrue(PresetRefs.problems(right, lookup).isEmpty(), "配对正确时不该有问题");

        Quest swapped = PresetRefs.resolve(quest(
                objective(map("preset", "reward-money")),
                reward(map("preset", "obj-chat"))), lookup);
        List<String> problems = PresetRefs.problems(swapped, lookup);
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("奖励预设"), problems.get(0));
        assertTrue(problems.get(1).contains("目标预设"), problems.get(1));
    }

    @Test
    @DisplayName("没引用预设的目标/奖励原样返回（两份配置是同一个 map）")
    void plainDefinitionsAreUntouched() {
        Quest quest = quest(objective(map("target", "STONE", "amount", 64)));

        Quest resolved = PresetRefs.resolve(quest, lookup);

        assertEquals(quest.objectives(), resolved.objectives());
        assertNull(resolved.objectives().get(0).presetId());
        assertTrue(PresetRefs.problems(resolved, lookup).isEmpty());
    }

    @Test
    @DisplayName("编辑器 JSON：引用给 preset / type / resolved，不给 properties，回传时不丢引用")
    void editorJsonCarriesTheReferenceOnly() {
        presets.put("mine-stone", new Preset(Preset.OBJECTIVES, "mine-stone", "挖石头", "break_block",
                map("target", "STONE", "amount", 64), ""));
        Quest quest = PresetRefs.resolve(quest(objective(map("preset", "mine-stone"))), lookup);

        Map<String, Object> json = QuestJson.toJson(quest);
        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) ((List<?>) json.get("objectives")).get(0);

        assertEquals("mine-stone", node.get("preset"));
        assertEquals("break_block", node.get("type"), "类型由预设提供，界面要能直接显示");
        assertEquals(map("target", "STONE", "amount", 64), node.get("resolved"), "resolved 是给界面显示的值");
        assertFalse(node.containsKey("properties"), "引用条目没有「任务自己写的字段」，不该送一个空表过去");

        // 再解析回来：引用还在（这是「编辑器保存一次就把预设写死」的防线）
        Quest back = QuestJson.fromJson(json);
        assertEquals("mine-stone", back.objectives().get(0).presetId());
        assertEquals(map("preset", "mine-stone"), back.objectives().get(0).authored());

        // 独立配置仍然照常带 properties
        Map<String, Object> plain = QuestJson.toJson(quest(objective(map("target", "STONE"))));
        @SuppressWarnings("unchecked")
        Map<String, Object> plainNode = (Map<String, Object>) ((List<?>) plain.get("objectives")).get(0);
        assertEquals(map("target", "STONE"), plainNode.get("properties"));
        assertNotNull(plainNode.get("type"));
    }

    // ---------- 辅助 ----------

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private static QuestObjective objective(Map<String, Object> authored) {
        // 两参构造：生效值先当作作者那份，展开由 PresetRefs.resolve 负责（与存储层读出来时一致）
        return new QuestObjective("", authored);
    }

    private static QuestReward reward(Map<String, Object> authored) {
        return new QuestReward("", authored);
    }

    private static Quest quest(QuestObjective... objectives) {
        return new Quest("q", "任务", List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(objectives), List.of(), 0.0, true);
    }

    private static Quest quest(QuestObjective objective, QuestReward reward) {
        return new Quest("q", "任务", List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(objective), List.of(reward), 0.0, true);
    }
}
