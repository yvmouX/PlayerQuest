package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 玩家数据 JSON 文件后端的测试。
 *
 * <p>为什么这层必须有测试：它把「任务记录」与「每日状态」塞进同一个文件，
 * 靠<b>一次原子改名</b>保证整体生效，而这两件事的写入路径共用同一份文件内容。
 * 最危险的错误模式是<b>不报错的</b>——某次保存顺手把 dailyState 抹掉，
 * 或者把损坏的文件当成空数据覆盖掉，代码不抛异常、任务列表看着也正常，
 * 只有玩家的刷新次数或手工修复过的数据悄悄没了。
 *
 * <p>因此这里重点钉住三件事：同一文件里两类数据互不挤占、
 * 损坏文件只读不写、写入不留临时文件。
 */
class JsonPlayerQuestRepositoryTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static JsonPlayerQuestRepository repository(Path folder, List<String> warnings) {
        return new JsonPlayerQuestRepository(folder, warnings::add);
    }

    private static PlayerQuest record(String questId, QuestType type, QuestStatus status) {
        PlayerQuest record = new PlayerQuest(PLAYER, questId, type, 1_700_000_000_000L, 1_700_086_400_000L, status);
        record.setProgress(0, 32);
        record.setProgress(1, 1);
        record.structureHash("hash-" + questId);
        return record;
    }

    private static Path fileOf(Path folder) {
        return folder.resolve(PLAYER + ".json");
    }

    private static String readFile(Path folder) throws Exception {
        return Files.readString(fileOf(folder), StandardCharsets.UTF_8);
    }

    /** 磁盘上是否已经出现某条记录：用来区分「已落盘」与「只在内存批次里」。 */
    private static boolean diskHas(Path folder, String questId) {
        try {
            return readFile(folder).contains("\"" + questId + "\"");
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("保存后按玩家读回：进度、状态、类型、时间戳、structureHash 完整往返")
    void roundTrip(@TempDir Path folder) {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());
        PlayerQuest original = record("daily_mine", QuestType.DAILY, QuestStatus.IN_PROGRESS);
        repository.save(original);

        // 一玩家一文件，文件名就是玩家 id
        assertTrue(Files.isRegularFile(fileOf(folder)), "应生成 <玩家id>.json");

        List<PlayerQuest> loaded = repository.findByPlayer(PLAYER);
        assertEquals(1, loaded.size());
        PlayerQuest quest = loaded.get(0);
        assertEquals(PLAYER, quest.playerId());
        assertEquals("daily_mine", quest.questId());
        assertEquals(QuestType.DAILY, quest.type());
        assertEquals(QuestStatus.IN_PROGRESS, quest.status());
        assertEquals(1_700_000_000_000L, quest.assignedAt());
        assertEquals(1_700_086_400_000L, quest.expiresAt());
        assertEquals("hash-daily_mine", quest.structureHash());
        assertEquals(32, quest.progress(0), "下标 0 的进度应往返");
        assertEquals(1, quest.progress(1), "下标 1 的进度应往返");
        assertEquals(2, quest.progress().size());
    }

    @Test
    @DisplayName("findByPlayer 只返回该玩家的记录")
    void findByPlayerIsScoped(@TempDir Path folder) {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());
        repository.save(record("daily_mine", QuestType.DAILY, QuestStatus.IN_PROGRESS));
        UUID other = UUID.fromString("99999999-8888-7777-6666-555555555555");
        repository.save(new PlayerQuest(other, "other_quest", QuestType.NORMAL, 1L, 2L, QuestStatus.CLAIMED));

        List<String> mine = repository.findByPlayer(PLAYER).stream().map(PlayerQuest::questId).toList();
        assertEquals(List.of("daily_mine"), mine, "不能串到别的玩家文件");
        assertEquals(List.of(), repository.findByPlayer(UUID.randomUUID()), "没有文件的玩家返回空表");
    }

    @Test
    @DisplayName("findActiveByPlayer 只返回进行中的记录")
    void findActiveFiltersStatus(@TempDir Path folder) {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());
        repository.save(record("doing", QuestType.DAILY, QuestStatus.IN_PROGRESS));
        repository.save(record("done", QuestType.DAILY, QuestStatus.CLAIMED));

        List<String> active = repository.findActiveByPlayer(PLAYER).stream().map(PlayerQuest::questId).toList();
        assertEquals(List.of("doing"), active);
    }

    @Test
    @DisplayName("同一份文件里装了任务记录与每日状态时，save(记录) 不得挤掉每日状态")
    void saveKeepsDailyState(@TempDir Path folder) {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());
        repository.save(record("daily_mine", QuestType.DAILY, QuestStatus.IN_PROGRESS));
        repository.saveDailyState(PLAYER, "2026-09-13", 2, 1_700_000_000_000L);

        repository.save(record("daily_farm", QuestType.DAILY, QuestStatus.IN_PROGRESS));

        PlayerQuestRepository.DailyState state = repository.findDailyState(PLAYER);
        assertNotNull(state, "保存任务记录不能顺手抹掉每日状态：刷新次数会静默清零");
        assertEquals("2026-09-13", state.period());
        assertEquals(2, state.refreshCount());
        assertEquals(1_700_000_000_000L, state.assignedAt());
        assertEquals(2, repository.findByPlayer(PLAYER).size(), "两条记录都应在文件里");
    }

    @Test
    @DisplayName("每日状态的读、写、覆盖与删除")
    void dailyStateRoundTrip(@TempDir Path folder) {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());

        assertNull(repository.findDailyState(PLAYER), "从未发放过时返回 null");
        assertTrue(repository.findByPlayer(PLAYER).isEmpty(), "读不存在的文件不报错");

        repository.saveDailyState(PLAYER, "2026-09-13", 0, 111L);
        PlayerQuestRepository.DailyState first = repository.findDailyState(PLAYER);
        assertNotNull(first);
        assertEquals("2026-09-13", first.period());
        assertEquals(0, first.refreshCount());
        assertEquals(111L, first.assignedAt());

        // 覆盖：同一天再刷新一次，计数与时刻都应变
        repository.saveDailyState(PLAYER, "2026-09-13", 1, 222L);
        PlayerQuestRepository.DailyState second = repository.findDailyState(PLAYER);
        assertNotNull(second);
        assertEquals(1, second.refreshCount(), "应覆盖而不是累加");
        assertEquals(222L, second.assignedAt());

        // 只写每日状态也不能把任务记录挤掉
        repository.save(record("daily_mine", QuestType.DAILY, QuestStatus.IN_PROGRESS));
        repository.saveDailyState(PLAYER, "2026-09-14", 3, 333L);
        assertEquals(1, repository.findByPlayer(PLAYER).size(), "写每日状态不能挤掉任务记录");
        assertEquals(3, repository.findDailyState(PLAYER).refreshCount());

        repository.deleteDailyState(PLAYER);
        assertNull(repository.findDailyState(PLAYER), "删除后应回到未发放状态");
        assertEquals(1, repository.findByPlayer(PLAYER).size(), "删每日状态不能连带删掉任务记录");
    }

    @Test
    @DisplayName("损坏的 JSON：读取按空处理并告警，但绝不改写磁盘上的原文件")
    void corruptedFileIsNeverRewritten(@TempDir Path folder) throws Exception {
        String broken = "{ 这不是合法 JSON";
        Files.createDirectories(folder);
        Files.writeString(fileOf(folder), broken, StandardCharsets.UTF_8);

        List<String> warnings = new ArrayList<>();
        JsonPlayerQuestRepository repository = repository(folder, warnings);

        assertTrue(repository.findByPlayer(PLAYER).isEmpty(), "损坏时按空处理而不是抛异常");
        assertNull(repository.findDailyState(PLAYER));
        assertEquals(broken, readFile(folder), "读取损坏文件不得覆盖原内容：用户还要手工修复");
        assertEquals(1, warnings.size(), "应恰好告警一次（每份文件只解析一次）: " + warnings);
        assertTrue(warnings.get(0).contains(PLAYER.toString()), "告警应带上玩家 id: " + warnings);
    }

    @Test
    @DisplayName("损坏的文件在下次写入时才被覆盖，且覆盖后能重新读出数据")
    void corruptedFileIsOverwrittenOnNextSave(@TempDir Path folder) throws Exception {
        Files.createDirectories(folder);
        Files.writeString(fileOf(folder), "not json at all", StandardCharsets.UTF_8);

        List<String> warnings = new ArrayList<>();
        JsonPlayerQuestRepository repository = repository(folder, warnings);
        repository.save(record("daily_mine", QuestType.DAILY, QuestStatus.IN_PROGRESS));

        assertTrue(warnings.stream().anyMatch(w -> w.contains("损坏")), "应告警: " + warnings);
        assertEquals(1, repository.findByPlayer(PLAYER).size());
        assertNull(repository.findDailyState(PLAYER), "无法解析的每日状态按没有处理，不阻止写入");
    }

    @Test
    @DisplayName("进度里的坏键坏值丢弃，浮点文本（5.0）容错读成整数")
    void progressToleratesDirtyEntries(@TempDir Path folder) throws Exception {
        String json = "{\"version\":1,\"quests\":[{\"questId\":\"q\",\"type\":\"DAILY\","
                + "\"status\":\"IN_PROGRESS\",\"assignedAt\":1,\"expiresAt\":2,"
                + "\"progress\":{\"0\":7,\" 1 \":\"12\",\"2\":\"5.0\",\"oops\":1,\"3\":\"abc\"}}]}";
        Files.createDirectories(folder);
        Files.writeString(fileOf(folder), json, StandardCharsets.UTF_8);

        Optional<PlayerQuest> loaded = repository(folder, new ArrayList<>()).find(PLAYER, "q");
        assertTrue(loaded.isPresent());
        assertEquals(7, loaded.get().progress(0));
        assertEquals(12, loaded.get().progress(1), "带空白的键应被容忍");
        assertEquals(5, loaded.get().progress(2), "\"5.0\" 这种浮点文本要能读成整数");
        assertEquals(0, loaded.get().progress(3), "坏值当 0（等价于丢弃这一项）");
        assertEquals(3, loaded.get().progress().size(), "坏键 oops 应被丢弃");
    }

    @Test
    @DisplayName("缺少 questId 的记录被跳过，类型/状态写错时退回默认值")
    void malformedRecordFields(@TempDir Path folder) throws Exception {
        String json = "{\"version\":1,\"quests\":["
                + "{\"type\":\"DAILY\",\"status\":\"IN_PROGRESS\"},"
                + "{\"questId\":\"keep\",\"type\":\"不存在\",\"status\":\"也不存在\"}]}";
        Files.createDirectories(folder);
        Files.writeString(fileOf(folder), json, StandardCharsets.UTF_8);

        List<PlayerQuest> loaded = repository(folder, new ArrayList<>()).findByPlayer(PLAYER);
        assertEquals(1, loaded.size(), "没有 questId 的记录无法定位，应跳过");
        assertEquals("keep", loaded.get(0).questId());
        assertEquals(QuestType.NORMAL, loaded.get(0).type(), "未知类型退回 NORMAL");
        assertEquals(QuestStatus.IN_PROGRESS, loaded.get(0).status(), "未知状态退回 IN_PROGRESS");
        assertEquals(0L, loaded.get(0).assignedAt());
        assertEquals("", loaded.get(0).structureHash(), "没有该字段时为空串，不编造");
    }

    @Test
    @DisplayName("写入是原子的：一次写入只留一个文件，不残留 .tmp")
    void writeLeavesNoTempFile(@TempDir Path folder) throws Exception {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());
        repository.save(record("daily_mine", QuestType.DAILY, QuestStatus.IN_PROGRESS));
        repository.saveDailyState(PLAYER, "2026-09-13", 1, 5L);

        try (var stream = Files.list(folder)) {
            List<String> names = stream.map(path -> path.getFileName().toString()).sorted().toList();
            assertEquals(List.of(PLAYER + ".json"), names, "不应残留临时文件: " + names);
        }
    }

    @Test
    @DisplayName("deleteByPlayerAndType 只删指定类型，别的类型与每日状态都留着")
    void deleteByTypeOnly(@TempDir Path folder) {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());
        repository.save(record("daily_mine", QuestType.DAILY, QuestStatus.IN_PROGRESS));
        repository.save(record("daily_farm", QuestType.DAILY, QuestStatus.IN_PROGRESS));
        repository.save(record("normal_mine", QuestType.NORMAL, QuestStatus.IN_PROGRESS));
        repository.saveDailyState(PLAYER, "2026-09-13", 1, 5L);

        repository.deleteByPlayerAndType(PLAYER, QuestType.DAILY);

        List<String> left = repository.findByPlayer(PLAYER).stream().map(PlayerQuest::questId).toList();
        assertEquals(List.of("normal_mine"), left);
        PlayerQuestRepository.DailyState state = repository.findDailyState(PLAYER);
        assertNotNull(state, "删任务不应动每日状态");
        assertEquals(1, state.refreshCount());
    }

    @Test
    @DisplayName("delete 只删指定任务，删不存在的任务不落盘")
    void deleteOneRecord(@TempDir Path folder) throws Exception {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());
        repository.save(record("daily_mine", QuestType.DAILY, QuestStatus.IN_PROGRESS));
        repository.save(record("daily_farm", QuestType.DAILY, QuestStatus.IN_PROGRESS));

        repository.delete(PLAYER, "daily_mine");
        assertEquals(List.of("daily_farm"),
                repository.findByPlayer(PLAYER).stream().map(PlayerQuest::questId).toList());

        // 删不存在的任务不该产生任何写入：磁盘内容逐字节不变
        String before = readFile(folder);
        repository.delete(PLAYER, "never_existed");
        assertEquals(before, readFile(folder));
    }

    @Test
    @DisplayName("distinctPlayerIds 列出有记录的玩家，非法文件名的文件被跳过")
    void distinctPlayerIdsSkipsIllegalNames(@TempDir Path folder) throws Exception {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());
        repository.save(record("daily_mine", QuestType.DAILY, QuestStatus.IN_PROGRESS));
        UUID other = UUID.fromString("99999999-8888-7777-6666-555555555555");
        repository.save(new PlayerQuest(other, "q", QuestType.NORMAL, 1L, 2L, QuestStatus.CLAIMED));

        List<UUID> ids = repository.distinctPlayerIds();
        assertEquals(2, ids.size());
        assertTrue(ids.contains(PLAYER));
        assertTrue(ids.contains(other));

        // 用户放了别的 .json（或重命名过的文件）时不能当成玩家，也不能让整个列表失败
        Files.writeString(folder.resolve("notes.json"), "{\"作者\":\"管理员\"}", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("readme.txt"), "纯文本", StandardCharsets.UTF_8);
        List<UUID> after = repository.distinctPlayerIds();
        assertEquals(2, after.size(), "非法文件名应被跳过: " + after);
        assertTrue(after.contains(PLAYER) && after.contains(other));
    }

    @Test
    @DisplayName("transaction 内的多次 save 攒到结束才落盘，批内能读到本次改动")
    void transactionBatchesWrites(@TempDir Path folder) {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());
        repository.save(record("daily_mine", QuestType.DAILY, QuestStatus.IN_PROGRESS));

        repository.transaction(() -> {
            repository.save(record("daily_farm", QuestType.DAILY, QuestStatus.IN_PROGRESS));
            repository.save(record("daily_fish", QuestType.DAILY, QuestStatus.IN_PROGRESS));

            // 批内读到的必须是本次批次的合并结果（先落盘再读回）
            assertEquals(3, repository.findByPlayer(PLAYER).size(),
                    "事务内的写入对同事务的读取必须可见");
            assertTrue(repository.find(PLAYER, "daily_fish").isPresent());
            // 且此刻磁盘上还没有它们：批内改动只在内存里，事务结束才一次落盘
            assertFalse(diskHas(folder, "daily_farm"), "事务未结束就不该落盘");
            assertFalse(diskHas(folder, "daily_fish"), "事务未结束就不该落盘");
        });

        List<String> ids = repository.findByPlayer(PLAYER).stream().map(PlayerQuest::questId).sorted().toList();
        assertEquals(List.of("daily_farm", "daily_fish", "daily_mine"), ids, "事务结束后应整体落盘");
    }

    @Test
    @DisplayName("事务里「删旧 + 写新 + 记状态」整体生效：中途读盘看不到半截状态")
    void transactionKeepsRefreshAtomic(@TempDir Path folder) {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());
        repository.save(record("old_daily", QuestType.DAILY, QuestStatus.IN_PROGRESS));
        repository.save(record("normal_mine", QuestType.NORMAL, QuestStatus.IN_PROGRESS));
        repository.saveDailyState(PLAYER, "2026-09-13", 0, 1L);

        // 每日刷新的真实形状：先删掉旧一批，再写新一批，最后记状态
        repository.transaction(() -> {
            repository.deleteByPlayerAndType(PLAYER, QuestType.DAILY);
            repository.save(record("new_daily_a", QuestType.DAILY, QuestStatus.IN_PROGRESS));
            repository.save(record("new_daily_b", QuestType.DAILY, QuestStatus.IN_PROGRESS));
            repository.saveDailyState(PLAYER, "2026-09-13", 1, 2L);

            // 关键：删除与写入必须都留在内存里。任一步在事务中途落盘，
            // 崩溃或异常后玩家就会看到「旧任务没了、新任务也没有」的空列表。
            assertTrue(diskHas(folder, "old_daily"), "事务中途不该已删掉旧任务");
            assertFalse(diskHas(folder, "new_daily_a"), "事务中途不该已写入新任务");
            assertEquals(List.of("new_daily_a", "new_daily_b", "normal_mine"),
                    repository.findByPlayer(PLAYER).stream().map(PlayerQuest::questId).sorted().toList());
            PlayerQuestRepository.DailyState inBatch = repository.findDailyState(PLAYER);
            assertNotNull(inBatch, "事务内应能读到本批次刚写的每日状态");
            assertEquals(1, inBatch.refreshCount());
        });

        List<String> ids = repository.findByPlayer(PLAYER).stream().map(PlayerQuest::questId).sorted().toList();
        assertEquals(List.of("new_daily_a", "new_daily_b", "normal_mine"), ids, "整体生效");
        PlayerQuestRepository.DailyState state = repository.findDailyState(PLAYER);
        assertNotNull(state);
        assertEquals(1, state.refreshCount(), "刷新次数要跟着一起落盘");
    }

    @Test
    @DisplayName("transaction 结束后事务状态被清理，之后的 save 立即落盘")
    void transactionStateIsClearedAfterwards(@TempDir Path folder) {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());
        repository.transaction(() -> repository.save(record("daily_mine", QuestType.DAILY, QuestStatus.IN_PROGRESS)));
        repository.save(record("daily_farm", QuestType.DAILY, QuestStatus.IN_PROGRESS));

        // 全新实例只读磁盘：能看到后一次 save，说明它没有留在内存批次里
        JsonPlayerQuestRepository reopened = repository(folder, new ArrayList<>());
        List<String> ids = reopened.findByPlayer(PLAYER).stream().map(PlayerQuest::questId).sorted().toList();
        assertEquals(List.of("daily_farm", "daily_mine"), ids);
    }

    @Test
    @DisplayName("事务抛异常时批次被丢弃，不留下半截数据")
    void transactionFailureDropsBatch(@TempDir Path folder) {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());

        try {
            repository.transaction(() -> {
                repository.save(record("daily_mine", QuestType.DAILY, QuestStatus.IN_PROGRESS));
                throw new IllegalStateException("模拟中途失败");
            });
        } catch (IllegalStateException expected) {
            // 异常应原样抛出，由调用方决定后续处理
        }

        System.out.println("DEBUG folder=" + folder + " exists=" + Files.exists(fileOf(folder)));
        try (var stream = Files.list(folder)) {
            stream.forEach(p -> System.out.println("DEBUG file=" + p.getFileName()));
        } catch (Exception ignored) {
            System.out.println("DEBUG list failed");
        }
        assertTrue(repository.findByPlayer(PLAYER).isEmpty(), "失败的事务不应落盘");
        // 事务状态已被清理：之后的写入必须正常落盘，而不是继续攒在已废弃的批次里
        repository.save(record("daily_farm", QuestType.DAILY, QuestStatus.IN_PROGRESS));
        assertEquals(List.of("daily_farm"), repository(folder, new ArrayList<>())
                .findByPlayer(PLAYER).stream().map(PlayerQuest::questId).toList());
    }

    @Test
    @DisplayName("playerId/questId 为空的记录被跳过并告警")
    void saveRejectsIncompleteRecord(@TempDir Path folder) {
        List<String> warnings = new ArrayList<>();
        JsonPlayerQuestRepository repository = repository(folder, warnings);

        repository.save(new PlayerQuest(null, "q", QuestType.NORMAL, 1L, 2L, QuestStatus.IN_PROGRESS));
        repository.save(new PlayerQuest(PLAYER, null, QuestType.NORMAL, 1L, 2L, QuestStatus.IN_PROGRESS));

        assertEquals(2, warnings.size(), "两条非法记录都应告警: " + warnings);
        assertFalse(Files.exists(fileOf(folder)), "不应为非法记录建文件");
    }

    @Test
    @DisplayName("读操作不落盘：磁盘上原本没有文件时，读取不会凭空建文件")
    void readsDoNotCreateFiles(@TempDir Path folder) {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());

        repository.findByPlayer(PLAYER);
        repository.findDailyState(PLAYER);
        repository.deleteByPlayerAndType(PLAYER, QuestType.DAILY);
        repository.delete(PLAYER, "q");

        assertFalse(Files.exists(fileOf(folder)), "读不该写盘");
    }

    @Test
    @DisplayName("同一份文件同时含记录与每日状态时，两类读取各拿到自己的部分")
    void mixedFileReadsBothParts(@TempDir Path folder) throws Exception {
        String json = "{\"version\":1,"
                + "\"quests\":[{\"questId\":\"q\",\"type\":\"NORMAL\",\"status\":\"COMPLETED\","
                + "\"assignedAt\":10,\"expiresAt\":20,\"progress\":{},\"structureHash\":\"h\"}],"
                + "\"dailyState\":{\"period\":\"2026-09-13\",\"refreshCount\":4,\"assignedAt\":30}}";
        Files.createDirectories(folder);
        Files.writeString(fileOf(folder), json, StandardCharsets.UTF_8);

        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());
        assertEquals(1, repository.findByPlayer(PLAYER).size());
        PlayerQuestRepository.DailyState state = repository.findDailyState(PLAYER);
        assertNotNull(state);
        assertEquals("2026-09-13", state.period());
        assertEquals(4, state.refreshCount());
        assertEquals(30L, state.assignedAt());

        // 字段类型写错（dailyState 不是对象）时应按「没有每日状态」处理，而不是整份读不出来
        Files.writeString(fileOf(folder), "{\"version\":1,\"quests\":[],\"dailyState\":\"2026-09-13\"}",
                StandardCharsets.UTF_8);
        JsonPlayerQuestRepository withWrongType = repository(folder, new ArrayList<>());
        assertNull(withWrongType.findDailyState(PLAYER));
        assertTrue(withWrongType.findByPlayer(PLAYER).isEmpty());

        // 缺 period 的每日状态同样视为没有，不编造周期
        Files.writeString(fileOf(folder), "{\"dailyState\":{\"refreshCount\":4}}", StandardCharsets.UTF_8);
        JsonPlayerQuestRepository missingPeriod = repository(folder, new ArrayList<>());
        assertNull(missingPeriod.findDailyState(PLAYER));
        assertTrue(missingPeriod.findByPlayer(PLAYER).isEmpty());
    }

    /** 供人工排查时对照：确认文件内容确实是单个 JSON 对象。 */
    @Test
    @DisplayName("文件内容是单个 JSON 对象，含 version 与 quests 两个键")
    void fileShape(@TempDir Path folder) throws Exception {
        JsonPlayerQuestRepository repository = repository(folder, new ArrayList<>());
        repository.save(record("daily_mine", QuestType.DAILY, QuestStatus.IN_PROGRESS));

        Map<String, Object> root = JsonCodec.readMapStrict(readFile(folder));
        assertNotNull(root);
        assertEquals(1, ((Number) root.get("version")).intValue());
        assertTrue(root.get("quests") instanceof List<?>);
        assertFalse(root.containsKey("dailyState"), "没有每日状态时不应写出该键");
    }
}
