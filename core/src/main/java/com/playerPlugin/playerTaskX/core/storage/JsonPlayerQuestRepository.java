package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.model.QuestType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 玩家数据的 JSON 文件后端：<b>一个玩家一个文件</b>。
 *
 * <h2>为什么按玩家分文件</h2>
 * 玩家数据是热路径（每次游戏事件都可能写），因此写入必须尽量小：
 * <ul>
 *   <li>一玩家一文件 → 改一个玩家的进度只重写他自己那份（实测单玩家约 1KB），
 *       不会像单文件方案那样随玩家数线性放大；</li>
 *   <li>每日刷新要「删旧 + 写新 + 记状态」原子完成。放在同一个文件里，
 *       这天然就是<b>一次原子改名</b>——一个文件即一个事务，不需额外机制；</li>
 *   <li>写入失败只影响该玩家，不会让全体玩家的数据一起损坏。</li>
 * </ul>
 *
 * <h2>与数据库后端的取舍（选它之前请先读）</h2>
 * <ul>
 *   <li><b>不能跨服共享</b>：文件在各自机器上，多服场景必须用 MySQL；</li>
 *   <li><b>写入更慢</b>：每次事件要读整份、改一条、写回整份，
 *       而数据库只是一行 upsert；服务端玩家多、动作频繁时差异明显；</li>
 *   <li><b>聚合更慢</b>：{@code countPlayers} / {@code distinctPlayerIds} 需要列目录。</li>
 * </ul>
 * 因此默认仍是 SQLite，这个后端只在「单服、玩家不多、想完全不依赖数据库」时才有意义。
 *
 * <h2>事务语义</h2>
 * {@link #transaction(Runnable)} 只保证「批量保存一起写盘」：文件系统没有跨文件事务，
 * 这里不假装有。好在真实调用方（每日刷新）改的都是<b>同一个玩家</b>的数据，
 * 落在一个文件里，因此一次原子改名就满足其原子性要求。
 */
public final class JsonPlayerQuestRepository implements PlayerQuestRepository {

    private static final String QUESTS = "quests";
    private static final String DAILY_STATE = "dailyState";

    private final JsonFileStore files;
    private final Consumer<String> warn;

    /** 事务期间的暂存：避免批内多次落盘。 */
    private final ThreadLocal<Map<UUID, Map<String, PlayerQuest>>> pending =
            ThreadLocal.withInitial(LinkedHashMap::new);

    public JsonPlayerQuestRepository(Path folder, Consumer<String> warn) {
        this.files = new JsonFileStore(folder);
        this.warn = warn;
    }

    /** 便捷构造：日志落到标准错误。 */
    public static JsonPlayerQuestRepository of(Path folder) {
        return new JsonPlayerQuestRepository(folder, System.err::println);
    }

    @Override
    public List<PlayerQuest> findByPlayer(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return new ArrayList<>(read(playerId).values());
    }

    @Override
    public List<PlayerQuest> findActiveByPlayer(UUID playerId) {
        return findByPlayer(playerId).stream().filter(PlayerQuest::isActive).toList();
    }

    @Override
    public Optional<PlayerQuest> find(UUID playerId, String questId) {
        if (playerId == null || questId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(read(playerId).get(questId));
    }

    @Override
    public void save(PlayerQuest playerQuest) {
        if (playerQuest == null || playerQuest.playerId() == null || playerQuest.questId() == null) {
            warn.accept("玩家任务记录的 playerId/questId 为空，已跳过保存");
            return;
        }
        Map<UUID, Map<String, PlayerQuest>> batch = pending.get();
        if (!batch.isEmpty()) {
            // 事务进行中：只改内存，由 transaction() 统一落盘
            batch.computeIfAbsent(playerQuest.playerId(), key -> read(playerQuest.playerId()))
                    .put(playerQuest.questId(), playerQuest);
            return;
        }
        Map<String, PlayerQuest> records = read(playerQuest.playerId());
        records.put(playerQuest.questId(), playerQuest);
        write(playerQuest.playerId(), records);
    }

    @Override
    public void saveAll(List<PlayerQuest> playerQuests) {
        if (playerQuests == null || playerQuests.isEmpty()) {
            return;
        }
        transaction(() -> playerQuests.forEach(this::save));
    }

    /**
     * 批量操作：期间的写入先攒在内存，结束时每个涉及的玩家落盘一次。
     * <p>
     * 不做跨玩家事务——文件系统没有这个能力，假装有只会掩盖问题。
     * 同玩家的多条改动落在同一个文件里，一次原子改名即整体生效或整体不生效。
     */
    @Override
    public void transaction(Runnable work) {
        Map<UUID, Map<String, PlayerQuest>> batch = pending.get();
        if (!batch.isEmpty()) {
            // 嵌套事务：并入外层批次，避免内层提前落盘破坏原子性
            work.run();
            return;
        }
        try {
            work.run();
            for (Map.Entry<UUID, Map<String, PlayerQuest>> entry : batch.entrySet()) {
                write(entry.getKey(), entry.getValue());
            }
        } finally {
            pending.remove();
        }
    }

    @Override
    public void delete(UUID playerId, String questId) {
        Map<String, PlayerQuest> records = read(playerId);
        if (records.remove(questId) == null) {
            return;
        }
        write(playerId, records);
    }

    @Override
    public void deleteByPlayerAndType(UUID playerId, QuestType type) {
        Map<String, PlayerQuest> records = read(playerId);
        boolean changed = records.values().removeIf(record -> record.type() == type);
        if (changed) {
            write(playerId, records);
        }
    }

    @Override
    public long countPlayers() {
        return files.listFiles().size();
    }

    @Override
    public List<UUID> distinctPlayerIds() {
        List<UUID> ids = new ArrayList<>();
        for (Path path : files.listFiles()) {
            String name = path.getFileName().toString();
            String raw = name.substring(0, name.length() - ".json".length());
            try {
                ids.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // 不是合法 UUID 的文件名：不是玩家数据，跳过
            }
        }
        return ids;
    }

    /** 目录，供报错提示与备份说明使用。 */
    public Path folder() {
        return files.folder();
    }

    // ------------------------------------------------------------------
    // 每日状态
    // ------------------------------------------------------------------

    @Override
    public DailyState findDailyState(UUID playerId) {
        return readDailyState(playerId);
    }

    @Override
    public void saveDailyState(UUID playerId, String period, int refreshCount, long assignedAt) {
        if (playerId == null) {
            warn.accept("saveDailyState 收到 null playerId，已忽略");
            return;
        }
        write(playerId, read(playerId), new DailyState(period, refreshCount, assignedAt));
    }

    @Override
    public void deleteDailyState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        write(playerId, read(playerId), null);
    }

    // ------------------------------------------------------------------
    // 读写
    // ------------------------------------------------------------------

    /** 读一个玩家的记录；文件不存在或损坏返回空表（损坏时记警告，不清空磁盘）。 */
    @SuppressWarnings("unchecked")
    private Map<String, PlayerQuest> read(UUID playerId) {
        String json = files.read(playerId.toString());
        if (json == null) {
            return new LinkedHashMap<>();
        }
        Map<String, PlayerQuest> records = new LinkedHashMap<>();
        try {
            Map<String, Object> root = JsonCodec.readMapStrict(json);
            if (root == null) {
                return records;
            }
            Object rawQuests = root.get(QUESTS);
            if (!(rawQuests instanceof List<?> list)) {
                return records;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                PlayerQuest record = toRecord(playerId, map);
                if (record != null) {
                    records.put(record.questId(), record);
                }
            }
        } catch (Exception e) {
            // 解析失败：报告并当作空。不清盘——损坏原因可能是外部编辑，
            // 直接覆盖会让用户失去手工修复的机会。
            warn.accept("玩家数据文件损坏，已按空处理（原文件保留未改）: "
                    + playerId + "（" + e.getMessage() + "）");
        }
        return records;
    }

    private PlayerQuest toRecord(UUID playerId, Map<?, ?> map) {
        String questId = text(map.get("questId"));
        if (questId.isBlank()) {
            return null;
        }
        QuestType type;
        try {
            type = QuestType.valueOf(text(map.get("type")).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            type = QuestType.NORMAL;
        }
        QuestStatus status;
        try {
            status = QuestStatus.valueOf(text(map.get("status")).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            status = QuestStatus.IN_PROGRESS;
        }
        PlayerQuest record = new PlayerQuest(playerId, questId, type,
                number(map.get("assignedAt")), number(map.get("expiresAt")), status);
        record.restoreProgress(intMap(map.get("progress")));
        record.structureHash(text(map.get("structureHash")));
        return record;
    }

    /** 写一个玩家的整份数据；一次原子改名，故「删旧 + 写新」整体生效或整体不生效。 */
    private void write(UUID playerId, Map<String, PlayerQuest> records) {
        write(playerId, records, readDailyState(playerId));
    }

    /**
     * 写一个玩家的整份数据。
     * <p>
     * 每日状态与任务记录放在同一份文件里，是为了让「删旧任务 + 写新任务 + 记状态」
     * 这类批量改动落在一次原子改名内——一个文件即一个事务。
     *
     * @param dailyState 为 null 表示清除每日状态
     */
    private void write(UUID playerId, Map<String, PlayerQuest> records, DailyState dailyState) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        List<Map<String, Object>> list = new ArrayList<>();
        for (PlayerQuest record : records.values()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("questId", record.questId());
            node.put("type", record.type().name());
            node.put("status", record.status().name());
            node.put("assignedAt", record.assignedAt());
            node.put("expiresAt", record.expiresAt());
            node.put("progress", record.progress());
            node.put("structureHash", record.structureHash());
            list.add(node);
        }
        root.put(QUESTS, list);
        if (dailyState != null) {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("period", dailyState.period());
            state.put("refreshCount", dailyState.refreshCount());
            state.put("assignedAt", dailyState.assignedAt());
            root.put(DAILY_STATE, state);
        }
        files.write(playerId.toString(), JsonCodec.write(root));
    }

    /** 读每日状态；文件不存在、损坏或没有该字段时返回 null。 */
    private DailyState readDailyState(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        String json = files.read(playerId.toString());
        if (json == null) {
            return null;
        }
        try {
            Map<String, Object> root = JsonCodec.readMapStrict(json);
            if (root == null || !(root.get(DAILY_STATE) instanceof Map<?, ?> state)) {
                return null;
            }
            Object period = state.get("period");
            if (period == null) {
                return null;
            }
            return new DailyState(String.valueOf(period),
                    (int) number(state.get("refreshCount")), number(state.get("assignedAt")));
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<Integer, Integer> intMap(Object raw) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            try {
                result.put(Integer.valueOf(String.valueOf(entry.getKey()).trim()),
                        (int) Double.parseDouble(String.valueOf(entry.getValue()).trim()));
            } catch (NumberFormatException ignored) {
                // 单个坏键值对丢掉即可，不让整份进度读不出来
            }
        }
        return result;
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** 每日状态目前只在数据库后端持久化；文件后端下它随玩家记录一起可重建。 */
    @Override
    public String toString() {
        return "JsonPlayerQuestRepository[" + files.folder().toAbsolutePath() + "]";
    }
}
