package com.playerPlugin.playerTaskX.core.engine;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.core.registry.BuiltIns;
import com.playerPlugin.playerTaskX.core.objective.ChatObjective;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.storage.InMemoryPlayerQuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目标结构指纹的测试。
 *
 * <p>要防的是「静默错配」：进度按目标<b>下标</b>记录，因此调换目标顺序后，
 * 旧进度会被套到别的目标上（挖了 32 个石头显示成"发言 32 次"），
 * 而语法校验查不出任何问题。这里的断言确保它变成「重置 + 告警」而不是静默错位。
 */
class StructureFingerprintTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private QuestRegistryImpl quests;
    private ObjectiveRegistryImpl objectiveTypes;
    private InMemoryPlayerQuestRepository repository;
    private ProgressService service;
    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        quests = new QuestRegistryImpl();
        objectiveTypes = new ObjectiveRegistryImpl();
        objectiveTypes.register(BuiltIns.objective("break_block"));
        objectiveTypes.register(new ChatObjective());
        repository = new InMemoryPlayerQuestRepository();
        service = new ProgressService(quests, objectiveTypes, repository);
        service.onStructureChanged((questId, playerId) -> warnings.add(questId + "/" + playerId));
    }

    /** 两个目标的任务：先挖石头、后发言。 */
    private static Quest twoObjectives() {
        return new Quest("q1", "两目标任务", List.of(), "PAPER", "", QuestType.NORMAL, List.of(),
                List.of(
                        QuestObjective.of("break_block", props("target", "STONE", "amount", 64)),
                        QuestObjective.of("chat", props("target", "", "amount", 1))),
                List.of(), 0.0, true);
    }

    /** 目标顺序调换后的同 id 任务。 */
    private static Quest twoObjectivesSwapped() {
        return new Quest("q1", "两目标任务", List.of(), "PAPER", "", QuestType.NORMAL, List.of(),
                List.of(
                        QuestObjective.of("chat", props("target", "", "amount", 1)),
                        QuestObjective.of("break_block", props("target", "STONE", "amount", 64))),
                List.of(), 0.0, true);
    }

    private static Map<String, Object> props(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    /** 放一份带指定结构摘要与进度的玩家记录，并载入索引。 */
    private void givenPlayerRecord(Quest quest, String structureHash, int slot, int progress) {
        quests.upsert(quest);
        PlayerQuest record = PlayerQuest.assign(PLAYER, quest, 0L, 0L);
        record.structureHash(structureHash);
        record.setProgress(slot, progress);
        repository.save(record);
        service.load(PLAYER);
    }

    @Test
    @DisplayName("结构摘要对目标顺序敏感：调换顺序得到不同摘要")
    void hashIsOrderSensitive() {
        assertNotEquals(ProgressService.structureHash(twoObjectives()),
                ProgressService.structureHash(twoObjectivesSwapped()),
                "顺序不同必须得到不同摘要，否则检测不出错位");
    }

    @Test
    @DisplayName("结构摘要对数量与参数敏感")
    void hashIsParameterSensitive() {
        String base = ProgressService.structureHash(twoObjectives());
        assertNotEquals(base, ProgressService.structureHash(twoObjectivesSwapped()));
        // 改数量
        Quest amountChanged = new Quest("q1", "两目标任务", List.of(), "PAPER", "", QuestType.NORMAL, List.of(),
                List.of(
                        QuestObjective.of("break_block", props("target", "STONE", "amount", 32)),
                        QuestObjective.of("chat", props("target", "", "amount", 1))),
                List.of(), 0.0, true);
        assertNotEquals(base, ProgressService.structureHash(amountChanged), "数量变化应改变摘要");
        // 改目标材质
        Quest targetChanged = new Quest("q1", "两目标任务", List.of(), "PAPER", "", QuestType.NORMAL, List.of(),
                List.of(
                        QuestObjective.of("break_block", props("target", "DIRT", "amount", 64)),
                        QuestObjective.of("chat", props("target", "", "amount", 1))),
                List.of(), 0.0, true);
        assertNotEquals(base, ProgressService.structureHash(targetChanged), "参数变化应改变摘要");
    }

    @Test
    @DisplayName("结构相同：进度保留，不重置也不告警")
    void matchingStructureKeepsProgress() {
        Quest quest = twoObjectives();
        givenPlayerRecord(quest, ProgressService.structureHash(quest), 0, 32);

        service.apply(ProgressContext.of(PLAYER, Trigger.BREAK_BLOCK, "STONE", 1));

        PlayerQuest stored = repository.find(PLAYER, "q1").orElseThrow();
        assertEquals(33, stored.progress(0), "结构没变，进度应继续累加");
        assertTrue(warnings.isEmpty(), "不应产生结构变化告警: " + warnings);
    }

    @Test
    @DisplayName("目标顺序被调换：进度重置为零并告警（而不是错配到别的目标）")
    void swappedStructureResetsProgress() {
        Quest original = twoObjectives();
        givenPlayerRecord(original, ProgressService.structureHash(original), 0, 32);

        // 管理员调换目标顺序后 reload
        quests.upsert(twoObjectivesSwapped());
        service.load(PLAYER);

        // 触发一次发言：若不做检测，玩家会看到"发言 33 次"
        service.apply(ProgressContext.of(PLAYER, Trigger.CHAT, "你好", 1));

        PlayerQuest stored = repository.find(PLAYER, "q1").orElseThrow();
        // 旧进度（原下标 0 = 32）必须被清掉，而不是变成"发言 32 次"
        assertEquals(1, stored.progress(0), "重置后本次发言正常累加到新下标 0");
        assertEquals(0, stored.progress(1), "换到下标 1 的挖石头目标应为零，未继承旧进度");
        assertEquals(1, warnings.size(), "必须留下告警: " + warnings);
        assertTrue(warnings.get(0).startsWith("q1/"), "告警应指明任务与玩家: " + warnings);
    }

    @Test
    @DisplayName("结构变化后摘要被更新，不会每次事件都重复重置")
    void hashIsRefreshedAfterReset() {
        Quest original = twoObjectives();
        givenPlayerRecord(original, ProgressService.structureHash(original), 0, 32);

        quests.upsert(twoObjectivesSwapped());
        service.load(PLAYER);
        service.apply(ProgressContext.of(PLAYER, Trigger.CHAT, "你好", 1));
        assertEquals(1, warnings.size());

        // 再来一次：摘要已是新的，不该再判定为变化
        service.apply(ProgressContext.of(PLAYER, Trigger.CHAT, "你好", 1));
        assertEquals(1, warnings.size(), "摘要更新后不应重复告警: " + warnings);
    }

    @Test
    @DisplayName("旧数据没有摘要：只补齐，不重置（没有依据不能清进度）")
    void missingHashIsBackfilledWithoutReset() {
        Quest quest = twoObjectives();
        givenPlayerRecord(quest, "", 0, 32);

        service.apply(ProgressContext.of(PLAYER, Trigger.BREAK_BLOCK, "STONE", 1));

        PlayerQuest stored = repository.find(PLAYER, "q1").orElseThrow();
        assertEquals(33, stored.progress(0), "未知摘要时不该重置进度");
        assertEquals(ProgressService.structureHash(quest), stored.structureHash(), "应补齐摘要");
        assertTrue(warnings.isEmpty(), "补齐不应告警: " + warnings);
    }

    @Test
    @DisplayName("已完成的记录因结构变化被重置时，状态也退回进行中（不能领奖）")
    void completedStatusRevertsOnReset() {
        Quest original = twoObjectives();
        quests.upsert(original);
        PlayerQuest record = PlayerQuest.assign(PLAYER, original, 0L, 0L);
        record.structureHash(ProgressService.structureHash(original));
        record.status(QuestStatus.COMPLETED);
        repository.save(record);
        service.load(PLAYER);

        quests.upsert(twoObjectivesSwapped());
        service.load(PLAYER);
        service.apply(ProgressContext.of(PLAYER, Trigger.CHAT, "你好", 1));

        PlayerQuest stored = repository.find(PLAYER, "q1").orElseThrow();
        assertEquals(QuestStatus.IN_PROGRESS, stored.status(),
                "进度清零后不该仍处于已完成状态（否则可以领奖）");
    }

    @Test
    @DisplayName("空目标列表的摘要稳定且可用")
    void emptyObjectivesHashIsStable() {
        Quest empty = new Quest("q0", "空任务", List.of(), "PAPER", "", QuestType.NORMAL, List.of(),
                List.of(), List.of(), 0.0, true);
        String first = ProgressService.structureHash(empty);
        String second = ProgressService.structureHash(empty);
        assertEquals(first, second);
        assertFalse(first.isBlank());
    }
}
