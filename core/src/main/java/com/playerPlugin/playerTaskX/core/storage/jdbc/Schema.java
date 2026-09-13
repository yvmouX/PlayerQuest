package com.playerPlugin.playerTaskX.core.storage.jdbc;

import com.playerPlugin.playerTaskX.core.storage.Dialect;
import com.playerPlugin.playerTaskX.core.storage.Database;
import com.playerPlugin.playerTaskX.core.storage.StorageException;

import java.util.List;

/**
 * 建表语句的唯一来源。
 * <p>
 * 所有语句经 {@link Dialect} 生成，因此同一份定义在 SQLite 与 MySQL 上都成立。
 */
public final class Schema {

    private Schema() {
    }

    /** 建表语句列表，按依赖顺序排列。 */
    public static List<String> createStatements(Dialect dialect) {
        String text = dialect.textType();
        String option = dialect.tableOption();

        return List.of(
                "CREATE TABLE IF NOT EXISTS quest ("
                        + "id VARCHAR(64) PRIMARY KEY, "
                        + "name " + text + " NOT NULL, "
                        + "description " + text + ", "
                        + "icon VARCHAR(64), "
                        + "category VARCHAR(64), "
                        + "type VARCHAR(16) NOT NULL, "
                        + "refresh_cost DOUBLE NOT NULL DEFAULT 0, "
                        + "enabled SMALLINT NOT NULL DEFAULT 1"
                        + ")" + option,

                // idx 在 MySQL 8 中是保留字，统一用方言转义
                "CREATE TABLE IF NOT EXISTS quest_objective ("
                        + "quest_id VARCHAR(64) NOT NULL, "
                        + dialect.quote("idx") + " INT NOT NULL, "
                        + "type VARCHAR(64) NOT NULL, "
                        + "properties " + text + ", "
                        + "PRIMARY KEY (quest_id, " + dialect.quote("idx") + ")"
                        + ")" + option,

                "CREATE TABLE IF NOT EXISTS quest_reward ("
                        + "quest_id VARCHAR(64) NOT NULL, "
                        + dialect.quote("idx") + " INT NOT NULL, "
                        + "type VARCHAR(64) NOT NULL, "
                        + "properties " + text + ", "
                        + "PRIMARY KEY (quest_id, " + dialect.quote("idx") + ")"
                        + ")" + option,

                "CREATE TABLE IF NOT EXISTS player_quest ("
                        + "player_id VARCHAR(36) NOT NULL, "
                        + "quest_id VARCHAR(64) NOT NULL, "
                        + "type VARCHAR(16) NOT NULL, "
                        + "assigned_at BIGINT NOT NULL, "
                        + "expires_at BIGINT NOT NULL DEFAULT 0, "
                        + "status VARCHAR(16) NOT NULL, "
                        + "progress " + text + ", "
                        // 接手该任务时目标列表的结构摘要：目标顺序变化后进度会整体错位，
                        // 靠它检测并重置，而不是静默套用到别的目标上
                        + "structure_hash VARCHAR(32), "
                        + "PRIMARY KEY (player_id, quest_id)"
                        + ")" + option,

                "CREATE TABLE IF NOT EXISTS daily_state ("
                        + "player_id VARCHAR(36) PRIMARY KEY, "
                        + "period VARCHAR(16) NOT NULL, "
                        + "refresh_count INT NOT NULL DEFAULT 0, "
                        + "assigned_at BIGINT NOT NULL DEFAULT 0"
                        + ")" + option,

                // 预设：目标/奖励的模板。kind 区分两类，不做成两张表——字段完全一致，
                // 拆表只会让读取多一次查询。
                "CREATE TABLE IF NOT EXISTS preset ("
                        + "kind VARCHAR(16) NOT NULL, "
                        + "id VARCHAR(64) PRIMARY KEY, "
                        + "name " + text + ", "
                        + "type VARCHAR(64) NOT NULL, "
                        + "properties " + text + ", "
                        + "description " + text
                        + ")" + option
        );
    }

    /**
     * 必要的索引，便于按玩家/类型查询。
     * <p>
     * 注意 MySQL 不支持 {@code CREATE INDEX IF NOT EXISTS}，
     * 因此索引统一由 {@link #initialize(Database)} 容错创建。
     */
    public static List<String> indexStatements() {
        return List.of(
                "CREATE INDEX idx_player_quest_player ON player_quest (player_id)",
                "CREATE INDEX idx_quest_type ON quest (type)"
        );
    }

    /** 建表并建索引；已存在的对象会被忽略。 */
    public static void initialize(Database database) {
        for (String statement : createStatements(database.dialect())) {
            database.executeInline(statement);
        }
        for (String statement : indexStatements()) {
            try {
                database.executeInline(statement);
            } catch (StorageException ignored) {
                // 索引已存在（MySQL 无 IF NOT EXISTS），忽略即可
            }
        }
    }
}
