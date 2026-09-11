package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcPlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.jdbc.Schema;
import com.playerPlugin.playerTaskX.core.storage.jdbc.SqliteDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 存储层集成测试：在内存 SQLite 上跑真实的 SQL。
 * <p>
 * 为什么必须有这一层：方言（upsert 语法、保留字转义、JSON 列）的问题
 * 在编译期完全看不出来——曾经就出现过 SQLite 上 {@code VALUES(col)} 语法错误
 * 导致所有写入路径失效的情况。用内存库跑一遍是最便宜的保护。
 */
class StorageIntegrationTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private SqliteDatabase sqlite;
    private Database database;
    private JdbcQuestRepository questRepository;
    private JdbcPlayerQuestRepository playerQuestRepository;

    @BeforeEach
    void setUp() throws SQLException {
        sqlite = SqliteDatabase.inMemory();
        database = sqlite.database();
        Schema.initialize(database);
        questRepository = new JdbcQuestRepository(database);
        playerQuestRepository = new JdbcPlayerQuestRepository(database);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    private Quest sampleQuest(String id) {
        return new Quest(id, "挖矿日常", List.of("挖 64 个石头", "奖励 500 金币"), "STONE_PICKAXE",
                "每日", QuestType.DAILY,
                List.of(
                        QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 64)),
                        QuestObjective.of("chat", Map.of("target", "你好", "amount", 1))),
                List.of(QuestReward.of("money", Map.of("amount", 500))),
                1000.0, true);
    }

    @Test
    @DisplayName("建表可重复执行（插件每次启动都会调用）")
    void schemaIsIdempotent() {
        Schema.initialize(database);
        Schema.initialize(database);
        assertEquals(0, questRepository.count());
    }

    @Test
    @DisplayName("任务完整往返：描述、目标、奖励、费用、启用状态")
    void questRoundTrip() {
        questRepository.save(sampleQuest("q1"));

        Quest loaded = questRepository.findById("q1").orElseThrow();
        assertEquals("挖矿日常", loaded.name());
        assertEquals(List.of("挖 64 个石头", "奖励 500 金币"), loaded.description());
        assertEquals("STONE_PICKAXE", loaded.icon());
        assertEquals("每日", loaded.category());
        assertEquals(QuestType.DAILY, loaded.type());
        assertEquals(1000.0, loaded.refreshCost());
        assertTrue(loaded.enabled());

        // 目标顺序与配置必须保持——玩家进度是按目标下标存的，错位会让进度张冠李戴
        assertEquals(2, loaded.objectives().size());
        assertEquals("break_block", loaded.objectives().get(0).type());
        assertEquals("STONE", loaded.objectives().get(0).string("target", ""));
        assertEquals(64, loaded.objectives().get(0).amount());
        assertEquals("chat", loaded.objectives().get(1).type());

        assertEquals(1, loaded.rewards().size());
        assertEquals("money", loaded.rewards().get(0).type());
        assertEquals(500, loaded.rewards().get(0).integer("amount", 0));
    }

    @Test
    @DisplayName("重复保存是覆盖而不是新增（验证 upsert 在各方言下都能工作）")
    void saveIsUpsertNotInsert() {
        questRepository.save(sampleQuest("q1"));
        Quest updated = new Quest("q1", "改名后", List.of(), "PAPER", "普通", QuestType.NORMAL,
                List.of(QuestObjective.of("kill", Map.of("target", "ZOMBIE", "amount", 5))),
                List.of(), 0.0, false);
        questRepository.save(updated);

        assertEquals(1, questRepository.count(), "同一 id 不应该产生第二行");
        Quest loaded = questRepository.findById("q1").orElseThrow();
        assertEquals("改名后", loaded.name());
        assertFalse(loaded.enabled());
        assertEquals(1, loaded.objectives().size(), "目标子表应被整体替换而不是追加");
        assertEquals("kill", loaded.objectives().get(0).type());
        assertTrue(loaded.rewards().isEmpty());
    }

    @Test
    @DisplayName("findAll 一次取回全部任务及其子树")
    void findAllReturnsEverything() {
        questRepository.save(sampleQuest("q1"));
        questRepository.save(sampleQuest("q2"));

        List<Quest> all = questRepository.findAll();
        assertEquals(2, all.size());
        assertTrue(all.stream().allMatch(quest -> quest.objectives().size() == 2));
        assertTrue(all.stream().allMatch(quest -> quest.rewards().size() == 1));
    }

    @Test
    @DisplayName("删除任务同时清理目标与奖励")
    void deleteRemovesChildren() {
        questRepository.save(sampleQuest("q1"));
        assertTrue(questRepository.delete("q1"));
        assertFalse(questRepository.exists("q1"));
        assertFalse(questRepository.delete("q1"), "重复删除应返回 false");

        long orphanObjectives = database.count("SELECT COUNT(*) FROM quest_objective WHERE quest_id = ?", "q1");
        long orphanRewards = database.count("SELECT COUNT(*) FROM quest_reward WHERE quest_id = ?", "q1");
        assertEquals(0, orphanObjectives);
        assertEquals(0, orphanRewards);
    }

    @Test
    @DisplayName("玩家进度完整往返（下标 → 计数）")
    void playerProgressRoundTrip() {
        Quest quest = sampleQuest("q1");
        questRepository.save(quest);

        PlayerQuest record = PlayerQuest.assign(PLAYER, quest, 1000L, 2000L);
        record.addProgress(0, 30, 64);
        record.addProgress(1, 1, 1);
        record.status(QuestStatus.COMPLETED);
        playerQuestRepository.save(record);

        PlayerQuest loaded = playerQuestRepository.find(PLAYER, "q1").orElseThrow();
        assertEquals(30, loaded.progress(0));
        assertEquals(1, loaded.progress(1));
        assertEquals(0, loaded.progress(99), "未记录的下标应为 0");
        assertEquals(QuestStatus.COMPLETED, loaded.status());
        assertEquals(QuestType.DAILY, loaded.type());
        assertEquals(1000L, loaded.assignedAt());
        assertEquals(2000L, loaded.expiresAt());
    }

    @Test
    @DisplayName("玩家任务重复保存是覆盖（复合主键 upsert）")
    void playerQuestSaveIsUpsert() {
        Quest quest = sampleQuest("q1");
        PlayerQuest record = PlayerQuest.assign(PLAYER, quest, 0L, 0L);
        playerQuestRepository.save(record);
        record.addProgress(0, 10, 64);
        playerQuestRepository.save(record);

        assertEquals(1, playerQuestRepository.findByPlayer(PLAYER).size());
        assertEquals(10, playerQuestRepository.find(PLAYER, "q1").orElseThrow().progress(0));
    }

    @Test
    @DisplayName("findActiveByPlayer 只返回进行中的任务")
    void findActiveFiltersByStatus() {
        Quest quest = sampleQuest("q1");
        PlayerQuest active = PlayerQuest.assign(PLAYER, quest, 0L, 0L);
        playerQuestRepository.save(active);

        PlayerQuest done = PlayerQuest.assign(PLAYER, sampleQuest("q2"), 0L, 0L);
        done.status(QuestStatus.CLAIMED);
        playerQuestRepository.save(done);

        List<PlayerQuest> actives = playerQuestRepository.findActiveByPlayer(PLAYER);
        assertEquals(1, actives.size());
        assertEquals("q1", actives.get(0).questId());
    }

    @Test
    @DisplayName("按类型清理玩家任务（每日刷新依赖它）")
    void deleteByPlayerAndType() {
        playerQuestRepository.save(PlayerQuest.assign(PLAYER, sampleQuest("q1"), 0L, 0L));
        PlayerQuest normal = PlayerQuest.assign(PLAYER,
                new Quest("n1", "普通", List.of(), "PAPER", null, QuestType.NORMAL,
                        List.of(QuestObjective.of("kill", Map.of("target", "ZOMBIE"))), List.of(), 0, true),
                0L, 0L);
        playerQuestRepository.save(normal);

        playerQuestRepository.deleteByPlayerAndType(PLAYER, QuestType.DAILY);

        List<PlayerQuest> remaining = playerQuestRepository.findByPlayer(PLAYER);
        assertEquals(1, remaining.size());
        assertEquals("n1", remaining.get(0).questId());
    }

    @Test
    @DisplayName("每日状态读写与覆盖")
    void dailyStateRoundTrip() {
        assertNull(playerQuestRepository.findDailyState(PLAYER));

        playerQuestRepository.saveDailyState(PLAYER, "2026-09-10", 0, 123L);
        JdbcPlayerQuestRepository.DailyState state = playerQuestRepository.findDailyState(PLAYER);
        assertNotNull(state);
        assertEquals("2026-09-10", state.period());
        assertEquals(0, state.refreshCount());

        playerQuestRepository.saveDailyState(PLAYER, "2026-09-10", 2, 456L);
        JdbcPlayerQuestRepository.DailyState updated = playerQuestRepository.findDailyState(PLAYER);
        assertEquals(2, updated.refreshCount(), "刷新次数应被覆盖而不是累加");
        assertEquals(456L, updated.assignedAt());
    }

    @Test
    @DisplayName("事务失败时回滚（每日刷新不能留下空任务列表）")
    void transactionRollsBackOnFailure() {
        playerQuestRepository.save(PlayerQuest.assign(PLAYER, sampleQuest("q1"), 0L, 0L));

        try {
            playerQuestRepository.transaction(() -> {
                playerQuestRepository.deleteByPlayerAndType(PLAYER, QuestType.DAILY);
                throw new IllegalStateException("模拟刷新中途失败");
            });
        } catch (IllegalStateException expected) {
            // 预期异常
        }

        assertEquals(1, playerQuestRepository.findByPlayer(PLAYER).size(),
                "事务回滚后原有任务必须还在");
    }

    @Test
    @DisplayName("脏数据不会让读取整体失败（非法 UUID 行被跳过）")
    void dirtyRowsAreSkipped() {
        questRepository.save(sampleQuest("q1"));
        // 直接插入一条 player_id 非法的行，模拟历史脏数据
        database.execute("INSERT INTO player_quest (player_id, quest_id, type, assigned_at, expires_at, status, progress) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", "not-a-uuid", "q1", "DAILY", 0L, 0L, "IN_PROGRESS", "{}");

        List<PlayerQuest> rows = playerQuestRepository.findByPlayer(PLAYER);
        assertTrue(rows.isEmpty(), "非法行应被跳过，而不是抛异常");
        // 合法的记录仍应正常读取
        playerQuestRepository.save(PlayerQuest.assign(PLAYER, sampleQuest("q1"), 0L, 0L));
        assertEquals(1, playerQuestRepository.findByPlayer(PLAYER).size());
    }

    @Test
    @DisplayName("JSON 列损坏时降级为空集合，不影响任务读取")
    void corruptedJsonDegradesGracefully() {
        questRepository.save(sampleQuest("q1"));
        database.execute("UPDATE quest_objective SET properties = ? WHERE quest_id = ?", "{ 这不是合法 JSON", "q1");

        Quest loaded = questRepository.findById("q1").orElseThrow();
        assertEquals("break_block", loaded.objectives().get(0).type());
        // 配置丢失后按默认值处理，但不应抛异常
        assertEquals(1, loaded.objectives().get(0).amount());
    }

    @Test
    @DisplayName("玩家计数按 DISTINCT 玩家")
    void countPlayersIsDistinct() {
        UUID other = UUID.fromString("99999999-8888-7777-6666-555555555555");
        playerQuestRepository.save(PlayerQuest.assign(PLAYER, sampleQuest("q1"), 0L, 0L));
        playerQuestRepository.save(PlayerQuest.assign(PLAYER, sampleQuest("q2"), 0L, 0L));
        playerQuestRepository.save(PlayerQuest.assign(other, sampleQuest("q1"), 0L, 0L));

        assertEquals(2, playerQuestRepository.countPlayers());
    }

    @Test
    @DisplayName("有任务记录的玩家 id 列表（网页编辑器管理端用）")
    void distinctPlayerIds() {
        UUID other = UUID.fromString("99999999-8888-7777-6666-555555555555");
        playerQuestRepository.save(PlayerQuest.assign(PLAYER, sampleQuest("q1"), 0L, 0L));
        playerQuestRepository.save(PlayerQuest.assign(PLAYER, sampleQuest("q2"), 0L, 0L));
        playerQuestRepository.save(PlayerQuest.assign(other, sampleQuest("q1"), 0L, 0L));

        List<UUID> ids = playerQuestRepository.distinctPlayerIds();
        assertEquals(2, ids.size(), "同一玩家的多条记录应只出现一次: " + ids);
        assertTrue(ids.contains(PLAYER) && ids.contains(other));
    }
}
