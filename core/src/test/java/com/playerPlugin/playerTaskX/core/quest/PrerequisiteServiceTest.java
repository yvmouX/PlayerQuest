package com.playerPlugin.playerTaskX.core.quest;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.storage.InMemoryQuestClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 前置任务判定与校验的测试。
 *
 * <p>要防的是两类「不报错的错误」：</p>
 * <ul>
 *   <li>前置判定只看当前任务记录 → 每日任务跨天被整批删除后链就断了（这里用内存账本钉住
 *       「判定只依赖账本」这一点）；</li>
 *   <li>配置成环或指向已删除的任务 → 玩家侧只表现为「这个任务一直不出现」，
 *       没有任何报错可查。</li>
 * </ul>
 */
class PrerequisiteServiceTest {

    private static final UUID ALICE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private QuestRegistryImpl quests;
    private InMemoryQuestClaimRepository claims;
    private PrerequisiteService service;

    @BeforeEach
    void setUp() {
        quests = new QuestRegistryImpl();
        claims = new InMemoryQuestClaimRepository();
        service = new PrerequisiteService(quests, claims);
    }

    // ---------- 解锁判定 ----------

    @Test
    @DisplayName("没有前置的任务恒为已解锁")
    void questWithoutPrerequisitesIsAlwaysUnlocked() {
        Quest quest = quest("q1");
        assertTrue(service.isUnlocked(ALICE, quest));
        assertTrue(service.unsatisfied(ALICE, quest).isEmpty());
        assertTrue(service.problems(quest).isEmpty());
    }

    @Test
    @DisplayName("多个前置必须全部领取才解锁，未满足的按配置顺序列出")
    void allPrerequisitesMustBeClaimed() {
        register(quest("p1"), quest("p2"));
        Quest quest = withPrerequisites("q1", "p1", "p2");

        assertEquals(List.of("p1", "p2"), service.unsatisfied(ALICE, quest), "一个都没领时全部未满足");
        assertFalse(service.isUnlocked(ALICE, quest));

        claims.given(ALICE, "p1");
        assertEquals(List.of("p2"), service.unsatisfied(ALICE, quest), "领了一个只剩另一个");

        claims.given(ALICE, "p2");
        assertTrue(service.isUnlocked(ALICE, quest));
    }

    @Test
    @DisplayName("前置判定按玩家隔离：别人领过不算")
    void claimedIsPerPlayer() {
        register(quest("p1"));
        Quest quest = withPrerequisites("q1", "p1");

        claims.given(BOB, "p1");
        assertFalse(service.isUnlocked(ALICE, quest), "BOB 领过不该让 ALICE 解锁");
        assertTrue(service.isUnlocked(BOB, quest));
    }

    @Test
    @DisplayName("前置任务不存在（或被删除）时永远挡住，而不是当成没有前置")
    void missingPrerequisiteBlocksForever() {
        Quest quest = withPrerequisites("q1", "ghost");

        assertEquals(List.of("ghost"), service.unsatisfied(ALICE, quest));
        assertFalse(service.isUnlocked(ALICE, quest));
        assertEquals(List.of("前置任务不存在: ghost"), service.problems(quest));
    }

    @Test
    @DisplayName("批量判定与单条判定等价（每日抽取用快照遍历整个池）")
    void batchJudgementMatchesSingle() {
        register(quest("p1"), quest("p2"));
        Quest quest = withPrerequisites("q1", "p1");
        claims.given(ALICE, "p1");

        Set<String> snapshot = service.claimedIds(ALICE);
        assertEquals(service.unsatisfied(ALICE, quest), service.unsatisfied(snapshot, quest));
        assertTrue(service.isUnlocked(snapshot, quest));
    }

    // ---------- 配置校验 ----------

    @Test
    @DisplayName("把自己列为前置会报出来")
    void selfReferenceIsReported() {
        register(quest("q1"));
        Quest quest = withPrerequisites("q1", "q1");
        assertEquals(List.of("前置任务不能是自己: q1"), service.problems(quest));
    }

    @Test
    @DisplayName("两任务互等（成环）会报出来，且两边都解锁不了")
    void twoQuestCycleIsReported() {
        register(withPrerequisites("a", "b"), withPrerequisites("b", "a"));

        List<String> problemsA = service.problems(quests.find("a").orElseThrow());
        assertEquals(1, problemsA.size());
        assertTrue(problemsA.get(0).startsWith("前置关系成环: "), problemsA.get(0));
        assertTrue(problemsA.get(0).contains("a -> b -> a"), problemsA.get(0));

        assertFalse(service.isUnlocked(ALICE, quests.find("a").orElseThrow()));
    }

    @Test
    @DisplayName("环不经过起点也能发现（a 等 b，b 等 c，c 等 b）")
    void cycleNotThroughStartIsReported() {
        register(withPrerequisites("a", "b"),
                withPrerequisites("b", "c"),
                withPrerequisites("c", "b"));

        List<String> problems = service.problems(quests.find("a").orElseThrow());
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("b -> c -> b"), problems.get(0));
    }

    @Test
    @DisplayName("长链但无环：不误报")
    void longChainIsNotACycle() {
        register(withPrerequisites("a", "b"), withPrerequisites("b", "c"), quest("c"));
        assertTrue(service.problems(quests.find("a").orElseThrow()).isEmpty());
    }

    @Test
    @DisplayName("菱形依赖（a 等 b、c；b、c 都等 d）不该被当成环")
    void diamondIsNotACycle() {
        register(withPrerequisites("a", "b", "c"),
                withPrerequisites("b", "d"),
                withPrerequisites("c", "d"),
                quest("d"));
        assertTrue(service.problems(quests.find("a").orElseThrow()).isEmpty());
    }

    @Test
    @DisplayName("前置任务被禁用会报出来：禁用后不会被抽取，也就永远拿不到")
    void disabledPrerequisiteIsReported() {
        register(quest("p1").withEnabled(false));
        Quest quest = withPrerequisites("q1", "p1");
        assertEquals(List.of("前置任务已禁用，将永远无法完成: p1"), service.problems(quest));
    }

    @Test
    @DisplayName("校验用待校验任务自己的前置，而不是注册表里的旧定义")
    void validationUsesCandidateItself() {
        // 注册表里 a 还没有前置；编辑器正要把它改成「a 等 b、b 等 a」
        register(quest("a"), withPrerequisites("b", "a"));
        assertTrue(service.problems(quests.find("a").orElseThrow()).isEmpty(), "旧定义无环");

        Quest candidate = withPrerequisites("a", "b");
        assertEquals(1, service.problems(candidate).size(), "新定义成环必须当场报出来");
    }

    // ---------- 辅助 ----------

    private void register(Quest... definitions) {
        for (Quest quest : definitions) {
            quests.upsert(quest);
        }
    }

    private static Quest quest(String id) {
        return new Quest(id, "任务 " + id, List.of(), "PAPER", null, QuestType.NORMAL, List.of(),
                List.of(QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 1))),
                List.of(), 0.0, true);
    }

    private static Quest withPrerequisites(String id, String... prerequisites) {
        return new Quest(id, "任务 " + id, List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(prerequisites),
                List.of(QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 1))),
                List.of(), 0.0, true);
    }
}
