package com.playerPlugin.playerTaskX.core.storage.jdbc;

import com.playerPlugin.playerTaskX.core.storage.Database;
import com.playerPlugin.playerTaskX.core.storage.codec.JsonCodec;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.model.QuestType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link PlayerQuestRepository} 的 JDBC 实现，并附带周期任务状态（{@code period_state}）的读写。
 * UUID 一律以 {@code toString()} 存 VARCHAR(36)（两种库对 UUID 支持不一致），读不出来的行只跳过而不让整份列表抛异常；写入统一走 {@link Dialect#upsert}，进度列用「下标 → 计数」的 JSON。
 */
public final class JdbcPlayerQuestRepository implements PlayerQuestRepository {

    /**
     * player_quest 列清单。
     * <p>
     * 与 {@link Dialect#upsert(String, String, String)} 的占位符顺序严格对应，绑定参数必须同序。
     */
    private static final String PLAYER_QUEST_COLUMNS =
            "player_id,quest_id,type,assigned_at,expires_at,status,progress,structure_hash";

    /**
     * 复合主键列。
     * <p>
     * {@link Dialect#upsert} 内部按逗号自行切分，因此这里传「逗号分隔」而非单个列名。
     */
    private static final String PLAYER_QUEST_KEY_COLUMNS = "player_id,quest_id";

    /**
     * period_state 列清单，同样与 upsert 占位符同序。
     * <p>
     * 主键是 {@code (player_id, type)}：四种周期各存一行，互不干扰。
     * 表名从旧版的 {@code daily_state} 改过来（旧表不会被读写，留在库里不影响任何事）。
     */
    private static final String PERIOD_STATE_COLUMNS = "player_id,type,period,refresh_count,assigned_at";

    private static final String PERIOD_STATE_KEY_COLUMNS = "player_id,type";

    private final Database database;

    private final String sqlSelectByPlayer;
    private final String sqlSelectActiveByPlayer;
    private final String sqlSelectOne;
    private final String sqlUpsert;
    private final String sqlDeleteOne;
    private final String sqlDeleteByType;

    private final String sqlSelectPeriodState;
    private final String sqlUpsertPeriodState;

    public JdbcPlayerQuestRepository(Database database) {
        this.database = Objects.requireNonNull(database, "database 不能为空");

        this.sqlSelectByPlayer = "SELECT " + PLAYER_QUEST_COLUMNS
                + " FROM player_quest WHERE player_id = ? ORDER BY assigned_at, quest_id";
        // 进行中 = status IN_PROGRESS；用参数绑定枚举名而不是写死字面量，避免拼写漂移
        this.sqlSelectActiveByPlayer = "SELECT " + PLAYER_QUEST_COLUMNS
                + " FROM player_quest WHERE player_id = ? AND status = ? ORDER BY assigned_at, quest_id";
        this.sqlSelectOne = "SELECT " + PLAYER_QUEST_COLUMNS
                + " FROM player_quest WHERE player_id = ? AND quest_id = ?";
        this.sqlUpsert = database.dialect().upsert(
                "player_quest", PLAYER_QUEST_KEY_COLUMNS, PLAYER_QUEST_COLUMNS);
        this.sqlDeleteOne = "DELETE FROM player_quest WHERE player_id = ? AND quest_id = ?";
        this.sqlDeleteByType = "DELETE FROM player_quest WHERE player_id = ? AND type = ?";

        this.sqlSelectPeriodState = "SELECT period, refresh_count, assigned_at"
                + " FROM period_state WHERE player_id = ? AND type = ?";
        this.sqlUpsertPeriodState = database.dialect().upsert(
                "period_state", PERIOD_STATE_KEY_COLUMNS, PERIOD_STATE_COLUMNS);
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    @Override
    public List<PlayerQuest> findByPlayer(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return valid(database.query(sqlSelectByPlayer, this::mapPlayerQuest, playerId.toString()));
    }

    @Override
    public List<PlayerQuest> findActiveByPlayer(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return valid(database.query(sqlSelectActiveByPlayer, this::mapPlayerQuest,
                playerId.toString(), QuestStatus.IN_PROGRESS.name()));
    }

    @Override
    public Optional<PlayerQuest> find(UUID playerId, String questId) {
        if (playerId == null || questId == null || questId.isBlank()) {
            return Optional.empty();
        }
        PlayerQuest row = database.queryOne(sqlSelectOne, this::mapPlayerQuest,
                playerId.toString(), questId);
        return Optional.ofNullable(row);
    }

    // ------------------------------------------------------------------
    // 写
    // ------------------------------------------------------------------

    @Override
    public void save(PlayerQuest playerQuest) {
        if (playerQuest == null) {
            warn("save 收到 null 玩家任务，已忽略");
            return;
        }
        database.transaction(() -> saveInternal(playerQuest));
    }

    @Override
    public void transaction(Runnable work) {
        database.transaction(work);
    }

    @Override
    public void delete(UUID playerId, String questId) {
        if (playerId == null || questId == null || questId.isBlank()) {
            return;
        }
        database.execute(sqlDeleteOne, playerId.toString(), questId);
    }

    @Override
    public void deleteByPlayerAndType(UUID playerId, QuestType type) {
        if (playerId == null || type == null) {
            return;
        }
        database.execute(sqlDeleteByType, playerId.toString(), type.name());
    }

    // ------------------------------------------------------------------
    // 周期任务状态（每日 / 每周 / 每月 / 自定义各一行）
    // ------------------------------------------------------------------

    /** 读取某种周期的状态；无记录返回 null（首次进入该周期即「还没有周期」）。 */
    @Override
    public PeriodState findPeriodState(UUID playerId, QuestType type) {
        if (playerId == null || type == null) {
            return null;
        }
        return database.queryOne(sqlSelectPeriodState,
                rs -> new PeriodState(
                        rs.getString("period"),
                        rs.getInt("refresh_count"),
                        rs.getLong("assigned_at")),
                playerId.toString(), type.name());
    }

    /** 写入（或覆盖）某种周期的状态。 */
    @Override
    public void savePeriodState(UUID playerId, QuestType type, String period, int refreshCount, long assignedAt) {
        if (playerId == null || type == null) {
            warn("savePeriodState 收到 null 参数，已忽略");
            return;
        }
        database.execute(sqlUpsertPeriodState,
                playerId.toString(),
                type.name(),
                // period 是 NOT NULL 列，脏参数宁可写空串也不要让保存失败
                period == null ? "" : period,
                Math.max(0, refreshCount),
                assignedAt);
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private void saveInternal(PlayerQuest playerQuest) {
        UUID playerId = playerQuest.playerId();
        String questId = playerQuest.questId();
        if (playerId == null || questId == null || questId.isBlank()) {
            // 两列都是主键的一部分，为空无法落库；跳过这一条而不是让整批保存失败
            warn("玩家任务记录的 playerId/questId 为空，已跳过保存");
            return;
        }
        QuestType type = playerQuest.type() == null ? QuestType.NORMAL : playerQuest.type();
        QuestStatus status = playerQuest.status() == null ? QuestStatus.IN_PROGRESS : playerQuest.status();

        database.execute(sqlUpsert,
                playerId.toString(),
                questId,
                type.name(),
                playerQuest.assignedAt(),
                playerQuest.expiresAt(),
                status.name(),
                JsonCodec.writeIntMap(playerQuest.progress()),
                playerQuest.structureHash());
    }

    private PlayerQuest mapPlayerQuest(ResultSet rs) throws SQLException {
        UUID playerId = parseUuid(rs.getString("player_id"));
        String questId = rs.getString("quest_id");
        if (playerId == null || questId == null || questId.isBlank()) {
            // 主键残缺的脏行：返回 null，由调用方过滤；不抛异常
            return null;
        }
        PlayerQuest playerQuest = new PlayerQuest(
                playerId,
                questId,
                parseType(rs.getString("type")),
                rs.getLong("assigned_at"),
                rs.getLong("expires_at"),
                parseStatus(rs.getString("status")));
        // 进度只能通过 restoreProgress 注入（PlayerQuest 没有公开的批量 setter）
        playerQuest.restoreProgress(JsonCodec.readIntMap(rs.getString("progress")));
        // 旧版本没有这一列，取不到就是空串（表示未知），此时不做任何重置
        playerQuest.structureHash(rs.getString("structure_hash"));
        return playerQuest;
    }

    /** 剔除映射阶段判定为脏的行（映射器无法跳过行，只能返回 null 再在这里滤掉）。 */
    private static List<PlayerQuest> valid(List<PlayerQuest> rows) {
        return rows.stream().filter(Objects::nonNull).toList();
    }

    /** 容错解析 UUID：非法值返回 null（跳过该行），不抛异常。 */
    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            warn("player_quest.player_id 不是合法 UUID，已跳过该行: " + raw);
            return null;
        }
    }

    /** 容错解析任务类型：非法/缺失按 NORMAL 处理。 */
    private static QuestType parseType(String raw) {
        return EnumText.parse(QuestType.class, raw, QuestType.NORMAL, "玩家任务的 type");
    }

    /** 容错解析状态：非法/缺失按「进行中」兜底，让状态未知的记录仍能被玩家看到；活跃任务是在 SQL 侧按 status 过滤的，因此兜底不会把脏数据混进活跃列表。 */
    private static QuestStatus parseStatus(String raw) {
        return EnumText.parse(QuestStatus.class, raw, QuestStatus.IN_PROGRESS, "玩家任务的状态");
    }

    private static void warn(String message) {
        EnumText.warn(message);
    }
}
