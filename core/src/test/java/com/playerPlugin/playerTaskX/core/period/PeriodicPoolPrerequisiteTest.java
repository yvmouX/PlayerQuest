package com.playerPlugin.playerTaskX.core.period;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.quest.PrerequisiteService;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.storage.InMemoryPlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.InMemoryQuestClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 每日抽取池与前置任务的交集测试。
 *
 * <p>要防的错误：抽到一个前置还没满足的任务。玩家在界面里看不到它被锁的原因
 * （前置未满足的任务本来就不展示），只会觉得这个任务做不了——而
 * {@code PeriodsTest} 里的纯函数测试查不出这一点，因为过滤发生在抽取之前。</p>
 */
class PeriodicPoolPrerequisiteTest {

    private static final UUID ALICE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private QuestRegistryImpl quests;
    private InMemoryQuestClaimRepository claims;
    private PeriodicService service;

    @BeforeEach
    void setUp() {
        quests = new QuestRegistryImpl();
        // d2 前置 d1；d1 无前置；n1 是常驻任务，不该出现在每日池里
        upsert(daily("d1"), daily("d2", "d1"), normal("n1"));
        claims = new InMemoryQuestClaimRepository();
        service = new PeriodicService(new PluginConfig(), quests, new InMemoryPlayerQuestRepository(),
                mock(ProgressService.class), new PrerequisiteService(quests, claims));
    }

    @Test
    @DisplayName("前置未领取的任务不进候选池")
    void lockedQuestIsNotInPool() {
        assertEquals(List.of("d1"), ids(service.availablePool(ALICE, QuestType.DAILY)));
    }

    @Test
    @DisplayName("领取过前置之后目标任务才进池")
    void questEntersPoolOncePrerequisiteClaimed() {
        claims.given(ALICE, "d1");
        assertEquals(List.of("d1", "d2"), ids(service.availablePool(ALICE, QuestType.DAILY)));
    }

    @Test
    @DisplayName("前置判定按玩家隔离：别人的领取记录不解锁我")
    void poolIsPerPlayer() {
        claims.given(UUID.randomUUID(), "d1");
        assertFalse(ids(service.availablePool(ALICE, QuestType.DAILY)).contains("d2"));
    }

    @Test
    @DisplayName("全局池仍然是全部每日任务（availablePool 只在它基础上过滤）")
    void globalPoolKeepsEverything() {
        assertEquals(List.of("d1", "d2"), ids(service.pool(QuestType.DAILY)));
    }

    @Test
    @DisplayName("任务链全部锁住时池为空——此时玩家当天没有每日任务，而不是抽到做不了的任务")
    void emptyPoolWhenEverythingIsLocked() {
        // 只放互相成环的两个任务：无论怎么抽都抽不出可做的任务
        QuestRegistryImpl locked = new QuestRegistryImpl();
        locked.upsert(daily("cycle_a", "cycle_b"));
        locked.upsert(daily("cycle_b", "cycle_a"));
        PeriodicService onlyLocked = new PeriodicService(new PluginConfig(), locked,
                new InMemoryPlayerQuestRepository(), mock(ProgressService.class),
                new PrerequisiteService(locked, new InMemoryQuestClaimRepository()));

        assertTrue(onlyLocked.availablePool(ALICE, QuestType.DAILY).isEmpty());
    }

    private static List<String> ids(List<Quest> quests) {
        return quests.stream().map(Quest::id).toList();
    }

    /** 注册若干任务定义（注册表接口是单条 upsert，这里只是省掉循环）。 */
    private void upsert(Quest... definitions) {
        for (Quest quest : definitions) {
            quests.upsert(quest);
        }
    }

    private static Quest daily(String id, String... prerequisites) {
        return new Quest(id, "每日 " + id, List.of(), "PAPER", "每日", QuestType.DAILY,
                List.of(prerequisites),
                List.of(QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 1))),
                List.of(), 0.0, true);
    }

    private static Quest normal(String id) {
        return new Quest(id, "常驻 " + id, List.of(), "PAPER", "常驻", QuestType.NORMAL, List.of(),
                List.of(QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 1))),
                List.of(), 0.0, true);
    }
}
