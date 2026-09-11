package com.playerPlugin.playerTaskX.core.quest;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.objective.BreakBlockObjective;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.RewardRegistryImpl;
import com.playerPlugin.playerTaskX.core.reward.ExpReward;
import com.playerPlugin.playerTaskX.core.reward.ItemReward;
import com.playerPlugin.playerTaskX.core.reward.RewardService;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 任务维护入口的不变量测试：保存与删除的「三处同步」。
 *
 * <p>落库、更新注册表、重建玩家索引必须成对发生——漏掉任何一步都是
 * 「不报错的错误」：库里改了但玩家进度仍按旧定义算，只有真机才能发现。
 * rebuildIndex 的效果无法从外部观察（引擎会惰性补建索引），因此用
 * Mockito 验证它确实被调用；ProgressService 是 final 类，Mockito 5
 * 默认的 inline mock maker 可以处理。</p>
 */
class QuestAdminServiceTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private FakeQuestRepository repository;
    private QuestRegistryImpl quests;
    private ProgressService progress;
    private QuestAdminService service;

    @BeforeEach
    void setUp() {
        repository = new FakeQuestRepository();
        quests = new QuestRegistryImpl();
        ObjectiveRegistryImpl objectiveTypes = new ObjectiveRegistryImpl();
        objectiveTypes.register(new BreakBlockObjective());
        RewardRegistryImpl rewardTypes = new RewardRegistryImpl();
        // 只登记恒可用的类型：money/points 的 available() 会探测 Bukkit 插件，单测环境没有服务端
        rewardTypes.register(new ExpReward());
        rewardTypes.register(new ItemReward());
        progress = mock(ProgressService.class);
        service = new QuestAdminService(repository, quests, objectiveTypes,
                new RewardService(quests, rewardTypes, new NoopPlayerQuestRepository()),
                progress, () -> List.of(PLAYER));
    }

    @Test
    @DisplayName("保存时落库、更新注册表、重建在线玩家索引三者成对发生")
    void saveSyncsAllThree() {
        service.save(quest("q1"));

        assertTrue(repository.exists("q1"), "任务应已落库");
        assertTrue(quests.find("q1").isPresent(), "注册表应已更新");
        verify(progress).rebuildIndex(PLAYER);
    }

    @Test
    @DisplayName("覆盖保存后注册表里是新定义，玩家进度按新定义算")
    void saveReplacesRegistryDefinition() {
        service.save(quest("q1"));
        Quest updated = questWithAmount("q1", 5);
        service.save(updated);

        assertEquals(5, quests.find("q1").orElseThrow().objectives().get(0).amount(),
                "注册表里必须是被覆盖后的新定义");
        verify(progress, times(2)).rebuildIndex(PLAYER);
    }

    @Test
    @DisplayName("删除已存在的任务：返回 true，清理注册表并重建索引")
    void deleteRemovesAndRebuilds() {
        service.save(quest("q1"));
        clearInvocations(progress);

        assertTrue(service.delete("q1"));
        assertFalse(repository.exists("q1"));
        assertFalse(quests.find("q1").isPresent(), "内存注册表必须同步清理");
        verify(progress).rebuildIndex(PLAYER);
    }

    @Test
    @DisplayName("删除不存在的 id：返回 false，不动注册表也不重建索引")
    void deleteMissingIsNoop() {
        service.save(quest("q1"));
        clearInvocations(progress);

        assertFalse(service.delete("missing"));
        assertTrue(quests.find("q1").isPresent(), "不相关的任务不应受影响");
        verify(progress, never()).rebuildIndex(any());
    }

    @Test
    @DisplayName("校验能发现未知的目标与奖励类型")
    void validateReportsUnknownTypes() {
        Quest broken = new Quest("broken", "坏任务", List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(QuestObjective.of("no_such_objective", Map.of("amount", 1))),
                List.of(QuestReward.of("no_such_reward", Map.of("amount", 1))), 0.0, true);

        List<String> problems = service.validate(broken);

        assertTrue(problems.stream().anyMatch(p -> p.contains("no_such_objective")),
                "应报告未知目标类型，实际: " + problems);
        assertTrue(problems.stream().anyMatch(p -> p.contains("no_such_reward")),
                "应报告未知奖励类型，实际: " + problems);
    }

    @Test
    @DisplayName("合法任务没有校验问题")
    void validQuestHasNoProblems() {
        assertTrue(service.validate(quest("q1")).isEmpty());
    }

    // ---------- 辅助方法 ----------

    private static Quest quest(String id) {
        return questWithAmount(id, 1);
    }

    private static Quest questWithAmount(String id, int amount) {
        return new Quest(id, "任务", List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(QuestObjective.of("break_block", Map.of("target", "STONE", "amount", amount))),
                List.of(QuestReward.of("exp", Map.of("amount", 100))), 0.0, true);
    }

    /** 内存版任务定义仓储：仅用于测试，不涉及 JDBC。 */
    private static final class FakeQuestRepository implements QuestRepository {

        private final Map<String, Quest> data = new LinkedHashMap<>();

        @Override
        public List<Quest> findAll() {
            return new ArrayList<>(data.values());
        }

        @Override
        public Optional<Quest> findById(String id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public void save(Quest quest) {
            data.put(quest.id(), quest);
        }

        @Override
        public void saveAll(List<Quest> quests) {
            quests.forEach(this::save);
        }

        @Override
        public boolean delete(String id) {
            return data.remove(id) != null;
        }

        @Override
        public long count() {
            return data.size();
        }
    }

    /** 空实现玩家仓储：RewardService 的校验路径不会触到它。 */
    private static final class NoopPlayerQuestRepository implements PlayerQuestRepository {

        @Override
        public List<PlayerQuest> findByPlayer(UUID playerId) {
            return List.of();
        }

        @Override
        public List<PlayerQuest> findActiveByPlayer(UUID playerId) {
            return List.of();
        }

        @Override
        public Optional<PlayerQuest> find(UUID playerId, String questId) {
            return Optional.empty();
        }

        @Override
        public void save(PlayerQuest playerQuest) {
        }

        @Override
        public void saveAll(List<PlayerQuest> playerQuests) {
        }

        @Override
        public void transaction(Runnable work) {
            work.run();
        }

        @Override
        public void delete(UUID playerId, String questId) {
        }

        @Override
        public void deleteByPlayerAndType(UUID playerId, QuestType type) {
        }

        @Override
        public long countPlayers() {
            return 0;
        }

        @Override
        public List<UUID> distinctPlayerIds() {
            return List.of();
        }
    }
}
