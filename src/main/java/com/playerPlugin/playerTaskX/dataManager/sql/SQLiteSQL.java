package com.playerPlugin.playerTaskX.dataManager.sql;

/**
 * SQLite SQL
 *
 * @author yvmoux
 * &#064;date  2025/10/31
 */
// TODO 这部分AI写的，有空可以检查一下
public class SQLiteSQL {
    public static final String sqlite_players_table =
            "CREATE TABLE IF NOT EXISTS players (" +
                    " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    " created_at DATETIME NOT NULL DEFAULT (CURRENT_TIMESTAMP)," +
                    " updated_at DATETIME NOT NULL DEFAULT (CURRENT_TIMESTAMP)," +
                    " player_uuid TEXT NOT NULL UNIQUE," +
                    " cycle_task_count INTEGER NOT NULL DEFAULT 0," +
                    " forever_task_count INTEGER NOT NULL DEFAULT 0," +
                    " limit_task_count INTEGER NOT NULL DEFAULT 0," +
                    " task_craft_times INTEGER NOT NULL DEFAULT 0," +
                    " task_kill_times INTEGER NOT NULL DEFAULT 0," +
                    " task_break_times INTEGER NOT NULL DEFAULT 0," +
                    " task_enchant_times INTEGER NOT NULL DEFAULT 0," +
                    " task_fishing_times INTEGER NOT NULL DEFAULT 0," +
                    " task_scissor_times INTEGER NOT NULL DEFAULT 0," +
                    " task_breed_times INTEGER NOT NULL DEFAULT 0," +
                    " task_tame_times INTEGER NOT NULL DEFAULT 0," +
                    " task_consume_times INTEGER NOT NULL DEFAULT 0," +
                    " task_trigger_times INTEGER NOT NULL DEFAULT 0," +
                    " task_drop_times INTEGER NOT NULL DEFAULT 0," +
                    " task_take_times INTEGER NOT NULL DEFAULT 0," +
                    " total_task_count INTEGER NOT NULL DEFAULT 0" +
                    ");";

    public static final String SQLITE_PLAYERS_INDEXES =
            // index on player_uuid (unique already exists on column but index explicit is fine)
            "CREATE INDEX IF NOT EXISTS idx_players_player_uuid ON players(player_uuid);";

    public static final String SQLITE_PLAYERS_TRIGGER_UPDATED_AT =
            // Trigger sets updated_at to CURRENT_TIMESTAMP after an update, but only when the update didn't already change updated_at.
            "CREATE TRIGGER IF NOT EXISTS trg_players_updated_at " +
                    "AFTER UPDATE ON players " +
                    "WHEN NEW.updated_at = OLD.updated_at " +
                    "BEGIN " +
                    "  UPDATE players SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id; " +
                    "END;";

    // SQLite version of players_tasks table + indexes + trigger
    public static final String sqlite_players_tasks_table =
            "CREATE TABLE IF NOT EXISTS players_tasks (" +
                    " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    " created_at DATETIME NOT NULL DEFAULT (CURRENT_TIMESTAMP)," +
                    " updated_at DATETIME NOT NULL DEFAULT (CURRENT_TIMESTAMP)," +
                    " player_uuid TEXT NOT NULL," +
                    " task_id TEXT NOT NULL," +
                    // enum-like columns implemented as TEXT + CHECK(...)
                    " task_type TEXT NOT NULL DEFAULT 'FOREVER' CHECK(task_type IN ('FOREVER','LIMIT','CYCLE'))," +
                    " task_target TEXT NOT NULL DEFAULT 'NONE' CHECK(task_target IN ('CRAFT','KILL','BREAK','ENCHANT','FISHING','SCISSOR','BREED','TAME','CONSUME','TRIGGER','DROP','TAKE','NONE'))," +
                    " task_progress INTEGER NOT NULL DEFAULT 0," +
                    " task_status TEXT NOT NULL DEFAULT 'UN_STARTED' CHECK(task_status IN ('UN_STARTED','IN_PROGRESS','COMPLETED','UN_COMPLETED','FAILED'))," +
                    " accept_at DATETIME NOT NULL DEFAULT (CURRENT_TIMESTAMP)," +
                    " finish_at DATETIME," +
                    " expire_at DATETIME" +
                    ");";

    public static final String SQLITE_PLAYERS_TASKS_INDEXES =
            "CREATE INDEX IF NOT EXISTS idx_players_tasks_player_uuid ON players_tasks(player_uuid);" +
                    // If you want an index by task_id as well:
                    "CREATE INDEX IF NOT EXISTS idx_players_tasks_task_id ON players_tasks(task_id);";

    public static final String SQLITE_PLAYERS_TASKS_TRIGGER_UPDATED_AT =
            "CREATE TRIGGER IF NOT EXISTS trg_players_tasks_updated_at " +
                    "AFTER UPDATE ON players_tasks " +
                    "WHEN NEW.updated_at = OLD.updated_at " +
                    "BEGIN " +
                    "  UPDATE players_tasks SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id; " +
                    "END;";
}
