package com.playerPlugin.playerTaskX.core.quest;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.registry.BuiltIns;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.RewardRegistryImpl;
import com.playerPlugin.playerTaskX.core.reward.ExpReward;
import com.playerPlugin.playerTaskX.core.reward.ItemReward;
import com.playerPlugin.playerTaskX.core.reward.RewardService;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

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

    @BeforeAll
    static void initBukkitLogger() {
        // reload/seedIfEmpty 会写日志（Bukkit.getLogger()），单测环境没有 server，桩一个
        Server server = mock(Server.class);
        org.mockito.Mockito.when(server.getLogger()).thenReturn(Logger.getLogger("PlayerTaskXTest"));
        Bukkit.setServer(server);
    }

    @BeforeEach
    void setUp() {
        repository = new FakeQuestRepository();
        quests = new QuestRegistryImpl();
        ObjectiveRegistryImpl objectiveTypes = new ObjectiveRegistryImpl();
        objectiveTypes.register(BuiltIns.objective("break_block"));
        // 「自定义钓鱼」依赖 CustomFishing（单测环境没有），用于验证「类型可用性」也被校验到
        objectiveTypes.register(BuiltIns.objective("custom_fish"));
        // 值域校验的用例要用的两个：剪毛（实体能力）与击杀（放行 mythic: 前缀）
        objectiveTypes.register(BuiltIns.objective("shear"));
        objectiveTypes.register(BuiltIns.objective("kill"));
        RewardRegistryImpl rewardTypes = new RewardRegistryImpl();
        // 只登记恒可用的类型：money/points 的 available() 会探测 Bukkit 插件，单测环境没有服务端
        rewardTypes.register(new ExpReward());
        rewardTypes.register(new ItemReward());
        progress = mock(ProgressService.class);
        service = new QuestAdminService(repository, quests, objectiveTypes,
                new RewardService(quests, rewardTypes, new NoopPlayerQuestRepository()),
                progress, () -> List.of(PLAYER), presets::get);
    }

    /** 测试用预设表：展开任务里的 {@code preset:} 引用时按它查（见 PresetRefsTest）。 */
    private final Map<String, Preset> presets = new LinkedHashMap<>();

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
    @DisplayName("引用预设的任务：库里存引用+覆盖项，注册表里是生效值；改预设后重载即刻生效")
    void presetReferenceSurvivesSaveAndFollowsThePreset() {
        presets.put("mine-stone", new Preset(Preset.OBJECTIVES, "mine-stone", "挖石头", "break_block",
                Map.of("target", "STONE", "amount", 64), ""));

        // 编辑器形状：引用 + 一个覆盖项（amount 改成 128）
        service.save(new Quest("q1", "任务", List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(new QuestObjective("", Map.of("preset", "mine-stone", "amount", 128))),
                List.of(), 0.0, true));

        Quest stored = repository.findById("q1").orElseThrow();
        assertEquals(Map.of("preset", "mine-stone", "amount", 128), stored.objectives().get(0).authored(),
                "库里存的应当是作者写的那份（引用 + 覆盖项），而不是展开后的值");
        Quest live = quests.find("q1").orElseThrow();
        assertEquals("break_block", live.objectives().get(0).type());
        assertEquals("STONE", live.objectives().get(0).properties().get("target"));
        assertEquals(128, live.objectives().get(0).properties().get("amount"));

        // 改预设：重载后引用它的任务跟着变（target 变了，没被覆盖的字段跟着走）
        presets.put("mine-stone", new Preset(Preset.OBJECTIVES, "mine-stone", "挖石头", "break_block",
                Map.of("target", "COBBLESTONE", "amount", 64), ""));
        service.reload();

        Quest after = quests.find("q1").orElseThrow();
        assertEquals("COBBLESTONE", after.objectives().get(0).properties().get("target"),
                "继承来的字段必须跟着预设变");
        assertEquals(128, after.objectives().get(0).properties().get("amount"), "覆盖项不受预设影响");
        assertEquals("mine-stone", after.objectives().get(0).presetId());
    }

    @Test
    @DisplayName("校验会报出悬空的预设引用")
    void validateReportsMissingPreset() {
        Quest broken = new Quest("q1", "任务", List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(new QuestObjective("", Map.of("preset", "nope"))),
                List.of(), 0.0, true);
        service.save(broken);

        List<String> problems = service.validate(quests.find("q1").orElseThrow());

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("nope")),
                "引用不存在的预设必须报出来: " + problems);
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
    @DisplayName("软依赖缺失的目标类型要报「不可用」，而不是让它静默不涨进度")
    void validateReportsUnavailableObjectiveType() {
        // 「自定义钓鱼」需要 CustomFishing，单测环境没有；target 怎么写都不重要
        Quest quest = new Quest("fishy", "钓鱼任务", List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(QuestObjective.of("custom_fish", Map.of("target", "", "amount", 1))),
                List.of(), 0.0, true);

        List<String> problems = service.validate(quest);

        assertTrue(problems.stream().anyMatch(p -> p.contains("custom_fish") && p.contains("CustomFishing")),
                "应报告该类型依赖缺失，实际: " + problems);
    }

    @Test
    @DisplayName("target 写了 mythic: 但服务端没有 MythicMobs：必须标出来（这种目标永远命中不了）")
    void validateReportsMythicTargetsWithoutThePlugin() {
        Quest quest = new Quest("mythic_kill", "讨伐自定义怪", List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(QuestObjective.of("kill", Map.of("target", "mythic:Boss", "amount", 1))),
                List.of(), 0.0, true);

        List<String> problems = service.validate(quest);

        assertTrue(problems.stream().anyMatch(p -> p.contains("mythic:Boss") && p.contains("MythicMobs")),
                "应报告该目标需要 MythicMobs，实际: " + problems);
    }

    @Test
    @DisplayName("合法任务没有校验问题")
    void validQuestHasNoProblems() {
        assertTrue(service.validate(quest("q1")).isEmpty());
    }

    @Test
    @DisplayName("值域校验接进了统一出口：给猪剪毛要报出来")
    void validateReportsImpossibleTargets() {
        // 剪毛的值域在实体侧，能离线判断，因此这条断言与真机一致
        Quest shearPig = questWithObjective("q2",
                QuestObjective.of("shear", Map.of("target", "PIG", "amount", 1)));
        assertTrue(service.validate(shearPig).stream().anyMatch(problem -> problem.contains("PIG")),
                "给猪剪毛永远不会命中，编辑器与 /ptxa list 都必须看到这条，实际: "
                        + service.validate(shearPig));

        // 认不出来的名字同样是死配置
        Quest shearGhost = questWithObjective("q3",
                QuestObjective.of("shear", Map.of("target", "NOT_A_MOB", "amount", 1)));
        assertTrue(service.validate(shearGhost).stream().anyMatch(problem -> problem.contains("NOT_A_MOB")));

        // 合法的照样没有额外问题（SHEEP 能剪毛、留空与 * 表示任意）
        assertTrue(service.validate(questWithObjective("q4",
                QuestObjective.of("shear", Map.of("target", "SHEEP", "amount", 1)))).isEmpty());
        assertTrue(service.validate(questWithObjective("q5",
                QuestObjective.of("shear", Map.of("target", "", "amount", 1)))).isEmpty());
        assertTrue(service.validate(questWithObjective("q6",
                QuestObjective.of("shear", Map.of("target", "*", "amount", 1)))).isEmpty());
    }

    @Test
    @DisplayName("值域校验放行别家插件的 id：该报的是「插件没装」，不是「值不可能命中」")
    void validateAllowsForeignIds() {
        for (String target : new String[]{"mythic:SkeletalKnight", "craftengine:default:torch"}) {
            Quest quest = questWithObjective("q7", QuestObjective.of("kill",
                    Map.of("target", target, "amount", 1)));
            List<String> problems = service.validate(quest);

            assertTrue(problems.stream().noneMatch(problem -> problem.contains("不是「")),
                    target + " 是别家插件的 id，离线判断不了，不该被值域校验判成非法，实际: " + problems);
            assertTrue(problems.stream().anyMatch(problem -> problem.contains("未安装")),
                    "该报的是「没装那个插件」（另有专门的检查），实际: " + problems);
        }
    }

    @Test
    @DisplayName("空库时写入全部示例任务，且走统一的保存入口（三处同步）")
    void seedWritesAllExamplesWhenEmpty() {
        service.seedIfEmpty(List.of(quest("example_a"), quest("example_b")));

        assertEquals(2, repository.count());
        assertTrue(quests.find("example_a").isPresent());
        assertTrue(quests.find("example_b").isPresent());
        verify(progress, times(2)).rebuildIndex(PLAYER);
    }

    @Test
    @DisplayName("库非空时不写入示例，绝不覆盖已有数据")
    void seedSkipsWhenDatabaseNotEmpty() {
        service.save(quest("user_quest"));
        clearInvocations(progress);

        service.seedIfEmpty(List.of(quest("example_a")));

        assertEquals(1, repository.count(), "已有数据不该被动");
        assertFalse(repository.exists("example_a"));
        verify(progress, never()).rebuildIndex(any());
    }

    // ---------- 辅助方法 ----------

    private static Quest quest(String id) {
        return questWithAmount(id, 1);
    }

    /** 指定目标的单目标任务：用于值域校验这类「配置里写了什么」的用例。 */
    private static Quest questWithObjective(String id, QuestObjective objective) {
        return new Quest(id, "任务", List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(objective), List.of(QuestReward.of("exp", Map.of("amount", 100))), 0.0, true);
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

        @Override
        public com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository.PeriodState findPeriodState(
                UUID playerId, QuestType type) {
            return null;
        }

        @Override
        public void savePeriodState(UUID playerId, QuestType type, String period, int refreshCount, long assignedAt) {
            // 测试替身不持久化周期状态
        }
    }
}
