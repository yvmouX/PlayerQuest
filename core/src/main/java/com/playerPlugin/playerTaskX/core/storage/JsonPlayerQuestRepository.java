package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.model.QuestType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    /**
     * 事务期间的暂存：避免批内多次落盘。
     * <p>
     * 只按「表非空」判断是否处于事务中是不够的——表恰恰是 {@link #save} 自己填的，
     * 因此<b>事务里的第一次写入会看不到事务而直接落盘</b>，
     * 每日刷新「先删旧任务再写新任务」的原子性就断在第一步。
     * 这里用 {@code inTransaction} 显式标记事务范围，批次表只负责装数据。
     */
    private final ThreadLocal<Boolean> inTransaction = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 事务期间攒下的数据：玩家 → 该玩家文件的完整内容。
     * <p>
     * 装的是「整份文件」而不是只有任务记录：任务记录与每日状态写在同一个文件里，
     * 只攒一半的话，事务结束时那次落盘会把另一半（本次事务没碰的部分）当成空写掉。
     */
    private final ThreadLocal<Map<UUID, Pending>> pending = ThreadLocal.withInitial(LinkedHashMap::new);

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
        return new ArrayList<>(read(playerId).quests().values());
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
        return Optional.ofNullable(read(playerId).quests().get(questId));
    }

    @Override
    public void save(PlayerQuest playerQuest) {
        if (playerQuest == null || playerQuest.playerId() == null || playerQuest.questId() == null) {
            warn.accept("玩家任务记录的 playerId/questId 为空，已跳过保存");
            return;
        }
        if (inTransaction.get()) {
            // 事务进行中：只改内存，由 transaction() 统一落盘
            liveInTransaction(playerQuest.playerId()).quests().put(playerQuest.questId(), playerQuest);
            return;
        }
        // 整份文件只解析一次：进度与每日状态同源，避免读两遍同一份内容
        FileData data = readAll(playerQuest.playerId());
        data.quests().put(playerQuest.questId(), playerQuest);
        write(playerQuest.playerId(), data.quests(), data.dailyState());
    }

    /**
     * 批量操作：期间的写入先攒在内存，结束时每个涉及的玩家落盘一次。
     * <p>
     * 不做跨玩家事务——文件系统没有这个能力，假装有只会掩盖问题。
     * 同玩家的多条改动落在同一个文件里，一次原子改名即整体生效或整体不生效。
     */
    @Override
    public void transaction(Runnable work) {
        if (inTransaction.get()) {
            // 嵌套事务：并入外层批次，避免内层提前落盘破坏原子性
            work.run();
            return;
        }
        Map<UUID, Pending> batch = pending.get();
        inTransaction.set(Boolean.TRUE);
        try {
            work.run();
            for (Map.Entry<UUID, Pending> entry : batch.entrySet()) {
                Pending state = entry.getValue();
                write(entry.getKey(), state.quests(), state.dailyState());
            }
        } finally {
            inTransaction.remove();
            pending.remove();
        }
    }

    @Override
    public void delete(UUID playerId, String questId) {
        if (inTransaction.get()) {
            // 与 save 一样攒进批次：否则「删旧 + 写新」的第一步就立刻落盘了
            liveInTransaction(playerId).quests().remove(questId);
            return;
        }
        FileData data = readAll(playerId);
        if (data.quests().remove(questId) == null) {
            return;
        }
        write(playerId, data.quests(), data.dailyState());
    }

    @Override
    public void deleteByPlayerAndType(UUID playerId, QuestType type) {
        if (inTransaction.get()) {
            liveInTransaction(playerId).quests().values().removeIf(record -> record.type() == type);
            return;
        }
        FileData data = readAll(playerId);
        if (!data.quests().values().removeIf(record -> record.type() == type)) {
            return;
        }
        write(playerId, data.quests(), data.dailyState());
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

    // ------------------------------------------------------------------
    // 每日状态
    // ------------------------------------------------------------------

    @Override
    public DailyState findDailyState(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        // 每日状态是任务记录的附属信息：文件坏了就当没有（任务记录那条路径会报出来），
        // 不必为同一个文件重复告警
        return read(playerId, false).dailyState();
    }

    @Override
    public void saveDailyState(UUID playerId, String period, int refreshCount, long assignedAt) {
        if (playerId == null) {
            warn.accept("saveDailyState 收到 null playerId，已忽略");
            return;
        }
        DailyState state = new DailyState(period, refreshCount, assignedAt);
        if (inTransaction.get()) {
            // 每日状态与任务记录同一份文件，事务里必须一起攒着改
            liveInTransaction(playerId).dailyState(state);
            return;
        }
        write(playerId, readAll(playerId).quests(), state);
    }

    @Override
    public void deleteDailyState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        if (inTransaction.get()) {
            liveInTransaction(playerId).dailyState(null);
            return;
        }
        write(playerId, readAll(playerId).quests(), null);
    }

    // ------------------------------------------------------------------
    // 读写
    // ------------------------------------------------------------------

    /**
     * 一个玩家文件的全部内容。
     * <p>
     * 任务记录与每日状态存在同一份文件里，分两次解析等于把同一段 JSON 读两遍；
     * 一次 {@code save} 内部的「读旧值 + 写新值」因此必须共用同一次解析结果。
     * {@code quests} 有意用可变表：事务里它会被就地修改。
     */
    private record FileData(Map<String, PlayerQuest> quests, DailyState dailyState) {
    }

    /**
     * 读取路径：事务里要读到本批次尚未落盘的改动，否则「写一条再读回来」会看不到。
     * <p>
     * 只有 {@code pending} 里已存在该玩家时才用批次——批次的装入本身就是从磁盘读的，
     * 不会凭空变出数据；事务结束后 {@code pending} 清空，读取回到磁盘。
     *
     * @param warnOnCorrupt 见 {@link #readAll(UUID, boolean)}
     */
    private FileData read(UUID playerId, boolean warnOnCorrupt) {
        Pending batched = pending.get().get(playerId);
        return batched == null
                ? readAll(playerId, warnOnCorrupt)
                : new FileData(batched.quests(), batched.dailyState());
    }

    /** 默认告警的读取：凡是要把内容写回去的调用点都走这条，损坏必须先被看见。 */
    private FileData read(UUID playerId) {
        return read(playerId, true);
    }

    /**
     * 事务批次里的一个玩家：改动就地累加，结束时整体写回一次。
     */
    private static final class Pending {

        private final Map<String, PlayerQuest> quests;
        private DailyState dailyState;

        private Pending(FileData data) {
            this.quests = data.quests();
            this.dailyState = data.dailyState();
        }

        private Map<String, PlayerQuest> quests() {
            return quests;
        }

        private DailyState dailyState() {
            return dailyState;
        }

        private void dailyState(DailyState state) {
            this.dailyState = state;
        }
    }

    /**
     * 取（必要时从磁盘装入）事务批次里该玩家的状态。
     * <p>
     * 装入只在第一次改动该玩家时发生，且用的是与写回同源的那份解析结果。
     */
    private Pending liveInTransaction(UUID playerId) {
        return pending.get().computeIfAbsent(playerId, key -> new Pending(readAll(key)));
    }

    /**
     * 读一个玩家的整份文件；不存在、损坏或字段类型不对时按缺失处理。
     * <p>
     * 损坏时只记警告、不改盘：损坏原因可能是用户手工编辑，
     * 直接覆盖会让他失去修复的机会。真正写回发生在下一次 {@link #write}。
     *
     * @param warnOnCorrupt 是否就该文件损坏记一条警告。整份解析只做一次，
     *                      两个字段因此共享同一条「损坏」结论；是否值得告警由调用点决定
     */
    private FileData readAll(UUID playerId, boolean warnOnCorrupt) {
        Map<String, PlayerQuest> records = new LinkedHashMap<>();
        if (playerId == null) {
            return new FileData(records, null);
        }
        String json = files.read(playerId.toString());
        if (json == null) {
            return new FileData(records, null);
        }
        try {
            Map<String, Object> root = JsonCodec.readMapStrict(json);
            if (root == null) {
                return new FileData(records, null);
            }
            if (root.get(QUESTS) instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        PlayerQuest record = toRecord(playerId, map);
                        if (record != null) {
                            records.put(record.questId(), record);
                        }
                    }
                }
            }
            return new FileData(records, toDailyState(root));
        } catch (Exception e) {
            // 解析失败：报告并当作空
            if (warnOnCorrupt) {
                warn.accept("玩家数据文件损坏，已按空处理（原文件保留未改）: "
                        + playerId + "（" + e.getMessage() + "）");
            }
            return new FileData(new LinkedHashMap<>(), null);
        }
    }

    /** 默认告警的读取：凡是要把内容写回去的调用点都走这条，损坏必须先被看见。 */
    private FileData readAll(UUID playerId) {
        return readAll(playerId, true);
    }

    /** 每日状态字段缺失或类型不对时返回 null（表示没有），不编造周期。 */
    private static DailyState toDailyState(Map<String, Object> root) {
        if (!(root.get(DAILY_STATE) instanceof Map<?, ?> state)) {
            return null;
        }
        Object period = state.get("period");
        if (period == null) {
            return null;
        }
        return new DailyState(String.valueOf(period),
                (int) number(state.get("refreshCount")), number(state.get("assignedAt")));
    }

    private static PlayerQuest toRecord(UUID playerId, Map<?, ?> map) {
        String questId = JsonCodec.text(map.get("questId"));
        if (questId.isBlank()) {
            return null;
        }
        PlayerQuest record = new PlayerQuest(playerId, questId,
                enumOrDefault(QuestType.class, map.get("type"), QuestType.NORMAL),
                number(map.get("assignedAt")), number(map.get("expiresAt")),
                enumOrDefault(QuestStatus.class, map.get("status"), QuestStatus.IN_PROGRESS));
        // progress 可能是「下标 → 计数」的对象；坏键坏值由 JsonCodec 丢弃
        record.restoreProgress(JsonCodec.asIntMap(map.get("progress")));
        record.structureHash(JsonCodec.text(map.get("structureHash")));
        return record;
    }

    /** 枚举名写错时退回默认值：一条脏记录不该让整个玩家的任务都读不出来。 */
    private static <E extends Enum<E>> E enumOrDefault(Class<E> type, Object raw, E fallback) {
        String name = JsonCodec.text(raw).toUpperCase(Locale.ROOT);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        return fallback;
    }

    /**
     * 写一个玩家的整份数据。
     * <p>
     * 每日状态与任务记录放在同一份文件里，是为了让「删旧任务 + 写新任务 + 记状态」
     * 这类批量改动落在一次原子改名内——一个文件即一个事务。
     *
     * @param dailyState 为 null 表示不写每日状态（清除）
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

    /**
     * 时间戳：数字原样取整，文本按十进制解析。
     * <p>
     * 与 {@link JsonCodec#asIntMap} 的容错不同，这里不接受 {@code "5.0"} 这类浮点文本——
     * 时间戳写成浮点本就说明数据有问题，宁可当 0（等同于「未知」）也不猜。
     */
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

    /** 每日状态目前只在数据库后端持久化；文件后端下它随玩家记录一起可重建。 */
    @Override
    public String toString() {
        return "JsonPlayerQuestRepository[" + files.folder().toAbsolutePath() + "]";
    }
}
