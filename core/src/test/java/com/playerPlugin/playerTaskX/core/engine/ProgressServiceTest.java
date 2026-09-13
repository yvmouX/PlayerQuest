package com.playerPlugin.playerTaskX.core.engine;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.core.objective.BreakBlockObjective;
import com.playerPlugin.playerTaskX.core.objective.ChatObjective;
import com.playerPlugin.playerTaskX.core.objective.InteractObjective;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 进度引擎测试：验证「动作 → 进度 → 完成」这条主链路。
 * <p>
 * 全部脱离服务端运行——{@link ProgressContext} 允许不带 Player 本体，
 * 因此这里不需要 MockBukkit 之类的框架。
 */
class ProgressServiceTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private QuestRegistryImpl quests;
    private ObjectiveRegistryImpl objectiveTypes;
    private FakePlayerQuestRepository repository;
    private ProgressService service;

    @BeforeEach
    void setUp() {
        quests = new QuestRegistryImpl();
        objectiveTypes = new ObjectiveRegistryImpl();
        objectiveTypes.register(new BreakBlockObjective());
        objectiveTypes.register(new ChatObjective());
        objectiveTypes.register(new InteractObjective());
        repository = new FakePlayerQuestRepository();
        service = new ProgressService(quests, objectiveTypes, repository);
    }

    @Test
    @DisplayName("没有任务时不产生任何变化")
    void noQuestsNoChange() {
        ApplyResult result = service.apply(context(Trigger.BREAK_BLOCK, "DIAMOND_ORE"));
        assertFalse(result.changed());
        assertTrue(result.completedQuests().isEmpty());
    }

    @Test
    @DisplayName("挖掘命中目标时累加进度")
    void breakBlockAdvancesProgress() {
        Quest quest = mineQuest("q1", "DIAMOND_ORE", 3);
        assign(quest);

        ApplyResult first = service.apply(context(Trigger.BREAK_BLOCK, "DIAMOND_ORE"));
        assertTrue(first.changed());
        assertEquals(1, stored("q1").progress(0));
        assertTrue(first.completedQuests().isEmpty());

        service.apply(context(Trigger.BREAK_BLOCK, "DIAMOND_ORE"));
        assertEquals(2, stored("q1").progress(0));
    }

    @Test
    @DisplayName("方块不匹配时不产生变化，也不写库")
    void nonMatchingTargetDoesNotAdvance() {
        Quest quest = mineQuest("q1", "DIAMOND_ORE", 3);
        assign(quest);

        ApplyResult result = service.apply(context(Trigger.BREAK_BLOCK, "STONE"));
        assertFalse(result.changed());
        assertEquals(0, stored("q1").progress(0));
    }

    @Test
    @DisplayName("触发类型不匹配时不产生变化")
    void differentTriggerDoesNotAdvance() {
        Quest quest = mineQuest("q1", "DIAMOND_ORE", 3);
        assign(quest);

        ApplyResult result = service.apply(context(Trigger.PLACE_BLOCK, "DIAMOND_ORE"));
        assertFalse(result.changed());
        assertEquals(0, stored("q1").progress(0));
    }

    @Test
    @DisplayName("进度按所需数量封顶，多余动作不再累加")
    void progressIsCappedAtRequired() {
        Quest quest = mineQuest("q1", "DIAMOND_ORE", 2);
        assign(quest);

        service.apply(context(Trigger.BREAK_BLOCK, "DIAMOND_ORE", 5));
        assertEquals(2, stored("q1").progress(0));

        ApplyResult afterCap = service.apply(context(Trigger.BREAK_BLOCK, "DIAMOND_ORE", 5));
        assertFalse(afterCap.changed());
    }

    @Test
    @DisplayName("全部目标达成时任务标记为完成并上报任务 id")
    void completesWhenAllObjectivesMet() {
        Quest quest = mineQuest("q1", "DIAMOND_ORE", 2);
        assign(quest);

        service.apply(context(Trigger.BREAK_BLOCK, "DIAMOND_ORE"));
        ApplyResult result = service.apply(context(Trigger.BREAK_BLOCK, "DIAMOND_ORE"));

        assertTrue(result.changed());
        assertEquals(List.of("q1"), result.completedQuests());
        assertEquals(QuestStatus.COMPLETED, stored("q1").status());
    }

    @Test
    @DisplayName("多目标任务需要每个目标都达成才算完成")
    void multiObjectiveRequiresAllObjectives() {
        Map<String, Object> mine = new LinkedHashMap<>();
        mine.put("target", "DIAMOND_ORE");
        mine.put("amount", 1);
        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("target", "你好");
        chat.put("amount", 1);
        Quest quest = new Quest("q2", "多目标", List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(QuestObjective.of("break_block", mine), QuestObjective.of("chat", chat)),
                List.of(QuestReward.of("money", Map.of("amount", 100))), 0.0, true);
        assign(quest);

        ApplyResult onlyMine = service.apply(context(Trigger.BREAK_BLOCK, "DIAMOND_ORE"));
        assertTrue(onlyMine.changed());
        assertTrue(onlyMine.completedQuests().isEmpty(), "只完成一个目标时不应上报完成");

        ApplyResult both = service.apply(context(Trigger.CHAT, "你好世界"));
        assertEquals(List.of("q2"), both.completedQuests());
    }

    @Test
    @DisplayName("已完成的任务不再累加进度")
    void completedQuestStopsAccumulating() {
        Quest quest = mineQuest("q1", "DIAMOND_ORE", 1);
        assign(quest);

        service.apply(context(Trigger.BREAK_BLOCK, "DIAMOND_ORE"));
        assertEquals(QuestStatus.COMPLETED, stored("q1").status());

        ApplyResult again = service.apply(context(Trigger.BREAK_BLOCK, "DIAMOND_ORE"));
        assertFalse(again.changed(), "已完成的任务不应再被推进");
        assertEquals(1, stored("q1").progress(0));
    }

    @Test
    @DisplayName("放弃任务后不再累加进度")
    void abandonedQuestIsNotAdvanced() {
        Quest quest = mineQuest("q1", "DIAMOND_ORE", 3);
        assign(quest);
        service.abandon(PLAYER, "q1");

        ApplyResult result = service.apply(context(Trigger.BREAK_BLOCK, "DIAMOND_ORE"));
        assertFalse(result.changed());
    }

    @Test
    @DisplayName("一次动作可同时推进多个任务")
    void oneActionAdvancesMultipleQuests() {
        assign(mineQuest("q1", "DIAMOND_ORE", 5));
        assign(mineQuest("q2", "DIAMOND_ORE", 5));

        ApplyResult result = service.apply(context(Trigger.BREAK_BLOCK, "DIAMOND_ORE"));

        assertTrue(result.changed());
        assertEquals(1, stored("q1").progress(0));
        assertEquals(1, stored("q2").progress(0));
    }

    @Test
    @DisplayName("索引缺失的玩家会被自动补建，不会因未登录而漏记进度")
    void indexIsLazilyBuilt() {
        Quest quest = mineQuest("q1", "DIAMOND_ORE", 5);
        // 任务已定义、玩家已有任务记录，但刻意不调用 service.load(...) 来模拟未触发登录事件
        quests.upsert(quest);
        repository.save(PlayerQuest.assign(PLAYER, quest, 0L, 0L));

        ApplyResult result = service.apply(context(Trigger.BREAK_BLOCK, "DIAMOND_ORE"));
        assertTrue(result.changed());
        assertEquals(1, stored("q1").progress(0));
    }

    @Test
    @DisplayName("进度回退时状态同步回到进行中，避免未达标却能领取")
    void setProgressRollsBackStatus() {
        Quest quest = mineQuest("q1", "DIAMOND_ORE", 1);
        assign(quest);
        service.apply(context(Trigger.BREAK_BLOCK, "DIAMOND_ORE"));
        assertEquals(QuestStatus.COMPLETED, stored("q1").status());

        service.setProgress(PLAYER, "q1", 0, 0);
        assertEquals(QuestStatus.IN_PROGRESS, stored("q1").status());
    }

    // ---------- 辅助方法 ----------

    private ProgressContext context(Trigger trigger, String target) {
        return context(trigger, target, 1);
    }

    private ProgressContext context(Trigger trigger, String target, int amount) {
        return ProgressContext.of(PLAYER, trigger, target, amount);
    }

    private Quest mineQuest(String id, String material, int amount) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("target", material);
        properties.put("amount", amount);
        return new Quest(id, "挖掘任务", List.of(), "DIAMOND_PICKAXE", null, QuestType.NORMAL,
                List.of(QuestObjective.of("break_block", properties)),
                List.of(QuestReward.of("money", Map.of("amount", 500))), 0.0, true);
    }

    private void assign(Quest quest) {
        quests.upsert(quest);
        repository.save(PlayerQuest.assign(PLAYER, quest, 0L, 0L));
        service.load(PLAYER);
    }

    private PlayerQuest stored(String questId) {
        return repository.find(PLAYER, questId).orElseThrow();
    }
}
