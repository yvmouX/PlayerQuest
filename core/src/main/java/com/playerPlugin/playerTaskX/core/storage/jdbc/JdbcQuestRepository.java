package com.playerPlugin.playerTaskX.core.storage.jdbc;

import com.playerPlugin.playerTaskX.core.storage.Database;
import com.playerPlugin.playerTaskX.core.storage.Dialect;
import com.playerPlugin.playerTaskX.core.storage.JsonCodec;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link QuestRepository} 的 JDBC 实现，SQLite 与 MySQL 共用（差异全部由 {@link Dialect} 承担）。
 * <p>
 * 关键取舍：
 * <ul>
 *   <li><b>读：批量取子树，规避 N+1</b>。{@link #findAll()} 只发 3 条 SQL（主表 + 目标表 + 奖励表），
 *       子表整表取出后在内存里按 {@code quest_id} 分组；否则 N 个任务就是 1+2N 次查询，
 *       网页编辑器一次刷新就能把 MySQL 打出几百次往返。</li>
 *   <li><b>写：先删子树再整体重建</b>。目标/奖励是一份整体配置，逐条 diff 只增加复杂度；
 *       整个写入包在一个事务里，中途失败会整体回滚，不会留下「主表在、子树没了」的残缺任务。</li>
 *   <li><b>所有 SQL 只出现在本文件</b>，且 {@code idx} 列名一律经 {@link Dialect#quote(String)} 转义
 *       （MySQL 8 中 {@code idx} 是保留字）。语句在构造时拼好，避免每行重复字符串拼接。</li>
 * </ul>
 */
public final class JdbcQuestRepository implements QuestRepository {

    /**
     * quest 主表列清单。
     * <p>
     * 与 {@link Dialect#upsert(String, String, String)} 的占位符顺序严格对应，绑定参数必须同序。
     */
    private static final String COLUMNS = "id,name,description,icon,category,type,refresh_cost,enabled";

    /** quest 的主键列（upsert 的冲突判定依据）。 */
    private static final String KEY_COLUMNS = "id";

    private final Database database;

    /** 转义后的 {@code idx} 列名（{@code `idx`}），供子表语句复用。 */
    private final String idx;

    private final String sqlSelectAll;
    private final String sqlSelectOne;
    private final String sqlUpsert;
    private final String sqlCount;
    private final String sqlDelete;
    private final String sqlSelectObjectives;
    private final String sqlSelectObjectivesOne;
    private final String sqlDeleteObjectives;
    private final String sqlInsertObjective;
    private final String sqlSelectRewards;
    private final String sqlSelectRewardsOne;
    private final String sqlDeleteRewards;
    private final String sqlInsertReward;

    public JdbcQuestRepository(Database database) {
        this.database = Objects.requireNonNull(database, "database 不能为空");
        this.idx = database.dialect().quote("idx");

        this.sqlSelectAll = "SELECT " + COLUMNS + " FROM quest ORDER BY id";
        this.sqlSelectOne = "SELECT " + COLUMNS + " FROM quest WHERE id = ?";
        // 参数顺序：主键列 → 全部列；Dialect 内部会自动把主键从 UPDATE 段排除
        this.sqlUpsert = database.dialect().upsert("quest", KEY_COLUMNS, COLUMNS);
        this.sqlCount = "SELECT COUNT(*) FROM quest";
        this.sqlDelete = "DELETE FROM quest WHERE id = ?";

        this.sqlSelectObjectives = "SELECT quest_id, type, properties FROM quest_objective"
                + " ORDER BY quest_id, " + idx;
        this.sqlSelectObjectivesOne = "SELECT quest_id, type, properties FROM quest_objective"
                + " WHERE quest_id = ? ORDER BY " + idx;
        this.sqlDeleteObjectives = "DELETE FROM quest_objective WHERE quest_id = ?";
        this.sqlInsertObjective = "INSERT INTO quest_objective (quest_id, " + idx + ", type, properties)"
                + " VALUES (?, ?, ?, ?)";

        this.sqlSelectRewards = "SELECT quest_id, type, properties FROM quest_reward"
                + " ORDER BY quest_id, " + idx;
        this.sqlSelectRewardsOne = "SELECT quest_id, type, properties FROM quest_reward"
                + " WHERE quest_id = ? ORDER BY " + idx;
        this.sqlDeleteRewards = "DELETE FROM quest_reward WHERE quest_id = ?";
        this.sqlInsertReward = "INSERT INTO quest_reward (quest_id, " + idx + ", type, properties)"
                + " VALUES (?, ?, ?, ?)";
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    @Override
    public List<Quest> findAll() {
        List<QuestRow> rows = database.query(sqlSelectAll, this::mapRow);
        if (rows.isEmpty()) {
            // 空库时连子表都不查，避免启动路径上白跑两条查询
            return List.of();
        }
        Map<String, List<QuestObjective>> objectives = group(
                database.query(sqlSelectObjectives, rs -> row(rs, JdbcQuestRepository::readObjective)));
        Map<String, List<QuestReward>> rewards = group(
                database.query(sqlSelectRewards, rs -> row(rs, JdbcQuestRepository::readReward)));

        List<Quest> quests = new ArrayList<>(rows.size());
        for (QuestRow row : rows) {
            quests.add(toQuest(row,
                    objectives.getOrDefault(row.id(), List.of()),
                    rewards.getOrDefault(row.id(), List.of())));
        }
        return quests;
    }

    @Override
    public Optional<Quest> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        QuestRow row = database.queryOne(sqlSelectOne, this::mapRow, id);
        if (row == null) {
            return Optional.empty();
        }
        // 单条任务不需要分组，直接映射成列表
        return Optional.of(toQuest(row,
                database.query(sqlSelectObjectivesOne, JdbcQuestRepository::readObjective, id),
                database.query(sqlSelectRewardsOne, JdbcQuestRepository::readReward, id)));
    }

    // ------------------------------------------------------------------
    // 写
    // ------------------------------------------------------------------

    @Override
    public void save(Quest quest) {
        if (quest == null) {
            warn("save 收到 null 任务，已忽略");
            return;
        }
        database.transaction(() -> saveInternal(quest));
    }

    @Override
    public boolean delete(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        // 事务内的 Runnable 没有返回值，用一个单元素数组把「删除前是否存在」带出来
        boolean[] deleted = new boolean[1];
        database.transaction(() -> {
            deleted[0] = database.count(sqlCount + " WHERE id = ?", id) > 0;
            // 先删子表再删主表，顺序固定，MySQL 上即使将来补外键也不会被挡
            database.execute(sqlDeleteObjectives, id);
            database.execute(sqlDeleteRewards, id);
            database.execute(sqlDelete, id);
        });
        return deleted[0];
    }

    @Override
    public long count() {
        return database.count(sqlCount);
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private void saveInternal(Quest quest) {
        String id = quest.id();
        if (id == null || id.isBlank()) {
            // id 是主键，为空时无法落库；跳过它而不是让整批导入失败
            warn("任务 id 为空，已跳过保存");
            return;
        }
        database.execute(sqlUpsert,
                id,
                // name 列是 NOT NULL，脏配置宁可写成空串也不要让整次保存抛异常
                quest.name() == null ? "" : quest.name(),
                JsonCodec.writeStringList(quest.description()),
                quest.icon(),
                quest.category(),
                (quest.type() == null ? QuestType.NORMAL : quest.type()).name(),
                quest.refreshCost(),
                // 布尔→SMALLINT：两种数据库都没有真正的 boolean 列
                quest.enabled() ? 1 : 0);

        // 子树整体重建：先清空，再按下标 0..n-1 插入。
        // type 列 NOT NULL：写成空串而不是跳过，保证 idx 与内存列表下标严格对齐
        // （玩家进度就是按目标下标存的，错位会让进度张冠李戴）
        database.execute(sqlDeleteObjectives, id);
        List<QuestObjective> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            database.execute(sqlInsertObjective, id, i,
                    objective.type() == null ? "" : objective.type(),
                    JsonCodec.write(objective.properties()));
        }

        database.execute(sqlDeleteRewards, id);
        List<QuestReward> rewards = quest.rewards();
        for (int i = 0; i < rewards.size(); i++) {
            QuestReward reward = rewards.get(i);
            database.execute(sqlInsertReward, id, i,
                    reward.type() == null ? "" : reward.type(),
                    JsonCodec.write(reward.properties()));
        }
    }

    /** 子表整表取出后按 quest_id 分组；脏行（quest_id 为空）已被映射阶段过滤掉。 */
    private static <T> Map<String, List<T>> group(List<Map.Entry<String, T>> rows) {
        Map<String, List<T>> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, T> row : rows) {
            if (row != null) {
                grouped.computeIfAbsent(row.getKey(), key -> new ArrayList<>()).add(row.getValue());
            }
        }
        return grouped;
    }

    /**
     * 子表行 → {@code quest_id → 元素}。
     * <p>
     * 返回 {@code Map.Entry} 而不是自定义的包装 record：唯一的目的是「带上父键以便分组」，
     * 为此各建一个 record 只会让三个文件角色重复。
     */
    private static <T> Map.Entry<String, T> row(ResultSet rs, RowReader<T> reader) throws SQLException {
        String questId = rs.getString("quest_id");
        return questId == null ? null : Map.entry(questId, reader.read(rs));
    }

    private QuestRow mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        if (id == null || id.isBlank()) {
            // 主键残缺的脏行：返回 null，由调用方过滤；不抛异常
            return null;
        }
        return new QuestRow(
                id,
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("icon"),
                rs.getString("category"),
                parseType(rs.getString("type")),
                rs.getDouble("refresh_cost"),
                rs.getInt("enabled") != 0);
    }

    private static QuestObjective readObjective(ResultSet rs) throws SQLException {
        // type 是 NOT NULL 列，能读到 null 说明数据被外部改坏了；保留行但标记为空类型
        String type = rs.getString("type");
        return new QuestObjective(type == null ? "" : type,
                JsonCodec.readMap(rs.getString("properties")));
    }

    private static QuestReward readReward(ResultSet rs) throws SQLException {
        String type = rs.getString("type");
        return new QuestReward(type == null ? "" : type,
                JsonCodec.readMap(rs.getString("properties")));
    }

    private static Quest toQuest(QuestRow row, List<QuestObjective> objectives, List<QuestReward> rewards) {
        return new Quest(row.id(), row.name(), JsonCodec.readStringList(row.description()),
                row.icon(), row.category(), row.type(), objectives, rewards,
                row.refreshCost(), row.enabled());
    }

    /** 容错解析任务类型：非法/缺失一律按 {@link QuestType#NORMAL} 处理，一条脏数据不该拖垮整个任务列表。 */
    private static QuestType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return QuestType.NORMAL;
        }
        try {
            return QuestType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn("任务类型非法，已按 NORMAL 处理: " + raw);
            return QuestType.NORMAL;
        }
    }

    private static void warn(String message) {
        System.err.println("[PlayerTaskX] " + message);
    }

    /** 从结果集读出一个子表元素；两种子表共用同一套分组逻辑。 */
    @FunctionalInterface
    private interface RowReader<T> {
        T read(ResultSet rs) throws SQLException;
    }

    /**
     * 主表行的中间形态：{@link Quest} 是不可变 record，目标/奖励必须在构造时传齐，
     * 因此先用它承接主表字段，等子表分组完成后再组装。
     */
    private record QuestRow(String id, String name, String description, String icon, String category,
                            QuestType type, double refreshCost, boolean enabled) {
    }
}
