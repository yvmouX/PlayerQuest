package com.playerPlugin.playerTaskX.dataManager;

public class SQL {
    public static final String players_sql = "CREATE TABLE IF NOT EXISTS players (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "created_at DATETIME NOT NULL," +
            "updated_at DATETIME NOT NULL," +
            "player_uuid TEXT NOT NULL," +
            "finish_CYCLE_times INTEGER NOT NULL DEFAULT 0," +
            "finish_FOREVER_times INTEGER NOT NULL DEFAULT 0," +
            "finish_LIMIT_times INTEGER NOT NULL DEFAULT 0," +
            "finish_TRIGGER_times INTEGER NOT NULL DEFAULT 0," +
            "task_CRAFT_times INTEGER NOT NULL DEFAULT 0," +
            "task_KILL_times INTEGER NOT NULL DEFAULT 0," +
            "task_BREAK_times INTEGER NOT NULL DEFAULT 0," +
            "task_ENCHANT_times INTEGER NOT NULL DEFAULT 0," +
            "task_FISHING_times INTEGER NOT NULL DEFAULT 0," +
            "task_SCISSOR_times INTEGER NOT NULL DEFAULT 0," +
            "task_BREED_times INTEGER NOT NULL DEFAULT 0," +
            "task_TAME_times INTEGER NOT NULL DEFAULT 0," +
            "task_CONSUME_times INTEGER NOT NULL DEFAULT 0," +
            "task_TRIGGER_times INTEGER NOT NULL DEFAULT 0," +
            "task_DROP_times INTEGER NOT NULL DEFAULT 0," +
            "task_TAKE_times INTEGER NOT NULL DEFAULT 0," +
            "times INTEGER_times NOT NULL DEFAULT 0)";
    public static final String players_tasks_sql = "CREATE TABLE IF NOT EXISTS players_tasks (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "created_at DATETIME NOT NULL," +
            "updated_at DATETIME NOT NULL," +
            "player_uuid TEXT NOT NULL," +
            "task_id TEXT NOT NULL," +
            "task_type INTEGER NOT NULL," +
            "task_target INTEGER NOT NULL," +
            "progress INTEGER NOT NULL," +
            "status INTEGER NOT NULL," +
            "started_time DATETIME NOT NULL," +
            "finished_time DATETIME NOT NULL," +
            "expired_time DATETIME NOT NULL)";
}
