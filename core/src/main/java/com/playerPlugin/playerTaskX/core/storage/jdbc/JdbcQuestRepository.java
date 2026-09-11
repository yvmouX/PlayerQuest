package com.playerPlugin.playerTaskX.core.storage.jdbc;

import com.playerPlugin.playerTaskX.core.storage.Dialect;
import com.playerPlugin.playerTaskX.core.storage.Database;
import com.playerPlugin.playerTaskX.core.storage.StorageException;
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
     * 读写共用同一份常量并保持顺序一致：{@link Dialect#upsert(String, String, String)} 生成的
     * 占位符个数与顺序完全由这串列名决定，绑定参数必须严格同序，共用常量可以杜绝两处漂移。
     */
    private static final String QUEST_COLUMNS =
            "id,name,description,icon,category,type,refresh_cost,enabled,sort_order";

    /** quest 的主键列（upsert 的冲突判定依据）。 */
    private static final String QUEST_KEY_COLUMNS = "id";

    private final Database database;

    /** 转义后的 {@code idx} 列名（{@code `idx`}），供子表语句复用。 */
    private final String idx;

    private final String sqlSelectAllQuests;
    private final String sqlSelectQuest;
    private final String sqlUpsertQuest;
    private final String sqlSelectSortOrder;
    private final String sqlCountQuests;
    private final String sqlCountQuestById;
    private final String sqlDeleteQuest;

    private final String sqlSelectAllObjectives;
    private final String sqlSelectObjectives;
    private final String sqlDeleteObjectives;
    private final String sqlInsertObjective;

    private final String sqlSelectAllRewards;
    private final String sqlSelectRewards;
    private final String sqlDeleteRewards;
    private final String sqlInsertReward;

    public JdbcQuestRepository(Database database) {
        this.database = Objects.requireNonNull(database, "database 不能为空");
        this.idx = database.dialect().quote("idx");

        this.sqlSelectAllQuests = "SELECT " + QUEST_COLUMNS + " FROM quest ORDER BY sort_order, id";
        this.sqlSelectQuest = "SELECT " + QUEST_COLUMNS + " FROM quest WHERE id = ?";
        // 参数顺序：主键列 → 全部列；Dialect 内部会自动把主键从 UPDATE 段排除
        this.sqlUpsertQuest = database.dialect().upsert("quest", QUEST_KEY_COLUMNS, QUEST_COLUMNS);
        this.sqlSelectSortOrder = "SELECT sort_order FROM quest WHERE id = ?";
        this.sqlCountQuests = "SELECT COUNT(*) FROM quest";
        this.sqlCountQuestById = "SELECT COUNT(*) FROM quest WHERE id = ?";
        this.sqlDeleteQuest = "DELETE FROM quest WHERE id = ?";

        this.sqlSelectAllObjectives = "SELECT quest_id, type, properties FROM quest_objective"
                + " ORDER BY quest_id, " + idx;
        this.sqlSelectObjectives = "SELECT type, properties FROM quest_objective"
                + " WHERE quest_id = ? ORDER BY " + idx;
        this.sqlDeleteObjectives = "DELETE FROM quest_objective WHERE quest_id = ?";
        this.sqlInsertObjective = "INSERT INTO quest_objective (quest_id, " + idx + ", type, properties)"
                + " VALUES (?, ?, ?, ?)";

        this.sqlSelectAllRewards = "SELECT quest_id, type, properties FROM quest_reward"
                + " ORDER BY quest_id, " + idx;
        this.sqlSelectRewards = "SELECT type, properties FROM quest_reward"
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
        List<QuestRow> rows = queryQuestRows(sqlSelectAllQuests);
        if (rows.isEmpty()) {
            // 空库时连子表都不查，避免启动路径上白跑两条查询
            return List.of();
        }
        Map<String, List<QuestObjective>> objectives = loadAllObjectives();
        Map<String, List<QuestReward>> rewards = loadAllRewards();
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
        QuestRow row = database.queryOne(sqlSelectQuest, this::mapQuestRow, id);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toQuest(row, loadObjectives(id), loadRewards(id)));
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
    public void saveAll(List<Quest> quests) {
        if (quests == null || quests.isEmpty()) {
            return;
        }
        // 单事务：批量导入要么全部落库，要么整体回滚，避免导入一半失败留下半份配置
        database.transaction(() -> {
            for (Quest quest : quests) {
                if (quest == null) {
                    continue;
                }
                saveInternal(quest);
            }
        });
    }

    @Override
    public boolean delete(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        // 事务内的 Runnable 没有返回值，用一个单元素数组把「删除前是否存在」带出来
        boolean[] deleted = new boolean[1];
        database.transaction(() -> {
            deleted[0] = database.count(sqlCountQuestById, id) > 0;
            // 先删子表再删主表，顺序固定，MySQL 上即使将来补外键也不会被挡
            database.execute(sqlDeleteObjectives, id);
            database.execute(sqlDeleteRewards, id);
            database.execute(sqlDeleteQuest, id);
        });
        return deleted[0];
    }

    @Override
    public long count() {
        return database.count(sqlCountQuests);
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /**
     * 单条任务的写入（不含事务边界），供 {@link #save(Quest)} 与 {@link #saveAll(List)} 复用。
     */
    private void saveInternal(Quest quest) {
        String id = quest.id();
        if (id == null || id.isBlank()) {
            // id 是主键，为空时无法落库；跳过它而不是让整批导入失败
            warn("任务 id 为空，已跳过保存");
            return;
        }
        QuestType type = quest.type() == null ? QuestType.NORMAL : quest.type();
        List<QuestObjective> objectives = quest.objectives();
        List<QuestReward> rewards = quest.rewards();

        database.execute(sqlUpsertQuest,
                id,
                // name 列是 NOT NULL，脏配置宁可写成空串也不要让整次保存抛异常
                quest.name() == null ? "" : quest.name(),
                JsonCodec.writeStringList(quest.description()),
                quest.icon(),
                quest.category(),
                type.name(),
                quest.refreshCost(),
                // 布尔→SMALLINT：两种数据库都没有真正的 boolean 列
                quest.enabled() ? 1 : 0,
                existingSortOrder(id));

        // 子树整体重建：先清空，再按下标 0..n-1 插入
        database.execute(sqlDeleteObjectives, id);
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            database.execute(sqlInsertObjective,
                    id,
                    i,
                    // type 列 NOT NULL；写成空串而不是跳过，保证 idx 与内存列表下标严格对齐
                    // （玩家进度就是按目标下标存的，错位会让进度张冠李戴）
                    objective.type() == null ? "" : objective.type(),
                    JsonCodec.write(objective.properties()));
        }

        database.execute(sqlDeleteRewards, id);
        for (int i = 0; i < rewards.size(); i++) {
            QuestReward reward = rewards.get(i);
            database.execute(sqlInsertReward,
                    id,
                    i,
                    reward.type() == null ? "" : reward.type(),
                    JsonCodec.write(reward.properties()));
        }
    }

    /**
     * 取已存在的排序值，新任务返回 0。
     * <p>
     * {@link Quest} 模型没有 sortOrder 字段（api 已冻结），若每行都写 0，
     * 会把网页编辑器/手工维护的展示顺序直接抹平；因此已存在的行沿用旧值，只对新行写 0。
     * 代价是每次保存多一次主键点查，相对子树重建可忽略。
     */
    private int existingSortOrder(String id) {
        Integer current = database.queryOne(sqlSelectSortOrder, rs -> rs.getInt("sort_order"), id);
        return current == null ? 0 : current;
    }

    /** 查询主表并把「主键为空」的脏行过滤掉。 */
    private List<QuestRow> queryQuestRows(String sql, Object... params) {
        List<QuestRow> rows = database.query(sql, this::mapQuestRow, params);
        List<QuestRow> result = new ArrayList<>(rows.size());
        for (QuestRow row : rows) {
            if (row != null) {
                result.add(row);
            }
        }
        return result;
    }

    private Map<String, List<QuestObjective>> loadAllObjectives() {
        List<ObjectiveRow> rows = database.query(sqlSelectAllObjectives, this::mapObjectiveRow);
        Map<String, List<QuestObjective>> grouped = new LinkedHashMap<>();
        for (ObjectiveRow row : rows) {
            if (row == null || row.questId() == null || row.objective() == null) {
                continue;
            }
            grouped.computeIfAbsent(row.questId(), key -> new ArrayList<>()).add(row.objective());
        }
        return grouped;
    }

    private Map<String, List<QuestReward>> loadAllRewards() {
        List<RewardRow> rows = database.query(sqlSelectAllRewards, this::mapRewardRow);
        Map<String, List<QuestReward>> grouped = new LinkedHashMap<>();
        for (RewardRow row : rows) {
            if (row == null || row.questId() == null || row.reward() == null) {
                continue;
            }
            grouped.computeIfAbsent(row.questId(), key -> new ArrayList<>()).add(row.reward());
        }
        return grouped;
    }

    private List<QuestObjective> loadObjectives(String questId) {
        List<QuestObjective> result = new ArrayList<>();
        for (QuestObjective objective : database.query(sqlSelectObjectives, this::mapObjective, questId)) {
            if (objective != null) {
                result.add(objective);
            }
        }
        return result;
    }

    private List<QuestReward> loadRewards(String questId) {
        List<QuestReward> result = new ArrayList<>();
        for (QuestReward reward : database.query(sqlSelectRewards, this::mapReward, questId)) {
            if (reward != null) {
                result.add(reward);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 结果集映射（全部容错：脏行返回 null，由调用方过滤）
    // ------------------------------------------------------------------

    private QuestRow mapQuestRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        if (id == null || id.isBlank()) {
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

    private ObjectiveRow mapObjectiveRow(ResultSet rs) throws SQLException {
        String questId = rs.getString("quest_id");
        QuestObjective objective = readObjective(rs);
        return questId == null || objective == null ? null : new ObjectiveRow(questId, objective);
    }

    private RewardRow mapRewardRow(ResultSet rs) throws SQLException {
        String questId = rs.getString("quest_id");
        QuestReward reward = readReward(rs);
        return questId == null || reward == null ? null : new RewardRow(questId, reward);
    }

    private QuestObjective mapObjective(ResultSet rs) throws SQLException {
        return readObjective(rs);
    }

    private QuestReward mapReward(ResultSet rs) throws SQLException {
        return readReward(rs);
    }

    private static QuestObjective readObjective(ResultSet rs) throws SQLException {
        String type = rs.getString("type");
        if (type == null) {
            // type 是 NOT NULL 列，能读到 null 说明数据被外部改坏了；保留行但标记为空类型
            type = "";
        }
        return new QuestObjective(type, JsonCodec.readMap(rs.getString("properties")));
    }

    private static QuestReward readReward(ResultSet rs) throws SQLException {
        String type = rs.getString("type");
        if (type == null) {
            type = "";
        }
        return new QuestReward(type, JsonCodec.readMap(rs.getString("properties")));
    }

    private static Quest toQuest(QuestRow row, List<QuestObjective> objectives, List<QuestReward> rewards) {
        return new Quest(
                row.id(),
                row.name(),
                JsonCodec.readStringList(row.description()),
                row.icon(),
                row.category(),
                row.type(),
                objectives,
                rewards,
                row.refreshCost(),
                row.enabled());
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

    /**
     * 主表行的中间形态：{@link Quest} 是不可变 record，目标/奖励必须在构造时传齐，
     * 因此先用它承接主表字段，等子表分组完成后再组装。
     */
    private record QuestRow(String id, String name, String description, String icon, String category,
                            QuestType type, double refreshCost, boolean enabled) {
    }

    /** 子表行的中间形态：带 quest_id 以便整表取出后分组。 */
    private record ObjectiveRow(String questId, QuestObjective objective) {
    }

    /** 子表行的中间形态：带 quest_id 以便整表取出后分组。 */
    private record RewardRow(String questId, QuestReward reward) {
    }
}
