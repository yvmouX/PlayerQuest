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
 * 预设引用（{@code objectives: [{preset: mine-stone, properties: {amount: 128}}]}）的测试。
 *
 * <p>要防的是三类静默错误：展开时覆盖项没赢（任务按预设的旧数量算）、保存时把继承来的值
 * 写成了显式覆盖（此后改预设再也不生效）、引用悬空却被当成「这个目标不存在」悄悄丢掉。</p>
 */
class PresetRefsTest {

    private final Map<String, Preset> presets = new LinkedHashMap<>();

    private final Function<String, Preset> lookup = id -> presets.get(id);

    @Test
    @DisplayName("展开：预设字段 ⊕ 任务自己的覆盖项，覆盖项赢")
    void overridesWin() {
        presets.put("mine-stone", new Preset(Preset.OBJECTIVES, "mine-stone", "挖石头", "break_block",
                map("target", "STONE", "amount", 64), ""));

        Quest quest = PresetRefs.resolve(quest(objective(map("preset", "mine-stone", "amount", 128))), lookup);

        QuestObjective objective = quest.objectives().get(0);
        assertEquals("break_block", objective.type(), "类型来自预设");
        assertEquals("STONE", objective.properties().get("target"), "没覆盖的字段用预设的");
        assertEquals(128, objective.properties().get("amount"), "覆盖项要赢过预设");
        assertFalse(objective.properties().containsKey("preset"), "preset 是记账键，不该进生效值");
        assertEquals("mine-stone", objective.presetId(), "引用本身要留着，否则下次保存就退化成独立配置");
    }

    @Test
    @DisplayName("悬空引用：类型留空、保留覆盖项，并报出「预设不存在」")
    void missingPresetIsReported() {
        Quest quest = PresetRefs.resolve(quest(objective(map("preset", "gone", "amount", 5))), lookup);

        assertEquals("", quest.objectives().get(0).type());
        assertEquals(5, quest.objectives().get(0).properties().get("amount"), "作者写的值不能丢");
        List<String> problems = PresetRefs.problems(quest, lookup);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("gone"), problems.get(0));
        assertTrue(problems.get(0).contains("不存在"), problems.get(0));
    }

    @Test
    @DisplayName("类别用错：拿奖励预设当目标用会被报出来")
    void kindMismatchIsReported() {
        presets.put("reward-exp", new Preset(Preset.REWARDS, "reward-exp", "奖励经验", "exp",
                map("amount", 100), ""));
        presets.put("obj-chat", new Preset(Preset.OBJECTIVES, "obj-chat", "发言", "chat",
                map("amount", 1), ""));

        Quest wrongReward = PresetRefs.resolve(quest(
                objective(map("preset", "obj-chat")),
                reward(map("preset", "reward-exp"))), lookup);
        assertTrue(PresetRefs.problems(wrongReward, lookup).isEmpty(), "配对正确时不该有问题");

        Quest swapped = PresetRefs.resolve(quest(
                objective(map("preset", "reward-exp")),
                reward(map("preset", "obj-chat"))), lookup);
        List<String> problems = PresetRefs.problems(swapped, lookup);
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("奖励预设"), problems.get(0));
        assertTrue(problems.get(1).contains("目标预设"), problems.get(1));
    }

    @Test
    @DisplayName("trim：只把「与预设不同」的字段留成覆盖项，继承来的字段不写死")
    void trimKeepsOnlyRealOverrides() {
        presets.put("mine-stone", new Preset(Preset.OBJECTIVES, "mine-stone", "挖石头", "break_block",
                map("target", "STONE", "amount", 64), ""));

        // 模拟编辑器回传：生效值被原样送回来（target/amount 都填着），其中 amount 被改过
        Quest roundTripped = quest(new QuestObjective("break_block",
                map("preset", "mine-stone", "target", "STONE", "amount", 128),
                map("preset", "mine-stone", "target", "STONE", "amount", 128)));

        Quest trimmed = PresetRefs.trim(roundTripped, lookup);

        Map<String, Object> authored = trimmed.objectives().get(0).authored();
        assertEquals("mine-stone", authored.get("preset"));
        assertEquals(128, authored.get("amount"), "改过的字段要留成覆盖项");
        assertFalse(authored.containsKey("target"), "与预设相同的字段不该被写死，否则以后改预设它不跟着变");
    }

    @Test
    @DisplayName("trim 后再展开，结果与原来一致（保存 → 载入是一次恒等变换）")
    void trimThenResolveIsStable() {
        presets.put("mine-stone", new Preset(Preset.OBJECTIVES, "mine-stone", "挖石头", "break_block",
                map("target", "STONE", "amount", 64), ""));

        Quest effective = PresetRefs.resolve(
                quest(objective(map("preset", "mine-stone", "target", "STONE", "amount", 128))), lookup);
        Quest again = PresetRefs.resolve(PresetRefs.trim(effective, lookup), lookup);

        assertEquals(effective.objectives().get(0).properties(), again.objectives().get(0).properties());
        assertEquals("mine-stone", again.objectives().get(0).presetId());
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
    @DisplayName("编辑器 JSON：给出 preset、覆盖项与生效值三样，回传时不丢引用")
    void editorJsonCarriesBoth() {
        presets.put("mine-stone", new Preset(Preset.OBJECTIVES, "mine-stone", "挖石头", "break_block",
                map("target", "STONE", "amount", 64), ""));
        Quest quest = PresetRefs.resolve(quest(objective(map("preset", "mine-stone", "amount", 128))), lookup);

        Map<String, Object> json = QuestJson.toJson(quest);
        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) ((List<?>) json.get("objectives")).get(0);

        assertEquals("mine-stone", node.get("preset"));
        assertEquals(128, ((Map<?, ?>) node.get("properties")).get("amount"), "properties 是任务自己写的那份");
        assertFalse(((Map<?, ?>) node.get("properties")).containsKey("target"),
                "继承来的字段不该出现在覆盖项里");
        assertEquals("STONE", ((Map<?, ?>) node.get("resolved")).get("target"), "resolved 是给界面直接显示的生效值");
        assertEquals(128, ((Map<?, ?>) node.get("resolved")).get("amount"));

        // 再解析回来：引用、覆盖项都还在（这是「编辑器保存一次就把预设写死」的防线）
        Quest back = QuestJson.fromJson(json);
        assertEquals("mine-stone", back.objectives().get(0).presetId());
        assertEquals(map("preset", "mine-stone", "amount", 128), back.objectives().get(0).authored());
        assertNotNull(QuestJson.toJson(back).get("objectives"));
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
        return new Quest("q", "任务", List.of(), "PAPER", null, QuestType.NORMAL, List.of(),
                List.of(objectives), List.of(), 0.0, true);
    }

    private static Quest quest(QuestObjective objective, QuestReward reward) {
        return new Quest("q", "任务", List.of(), "PAPER", null, QuestType.NORMAL, List.of(),
                List.of(objective), List.of(reward), 0.0, true);
    }
}
