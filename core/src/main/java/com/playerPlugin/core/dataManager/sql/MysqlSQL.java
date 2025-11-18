package com.playerPlugin.core.dataManager.sql;

/**
 * MySQL SQL
 *
 * @author yvmoux
 * &#064;date  2025/10/31
 */
public class MysqlSQL {

    public static final String mysql_task_statistics_sql = "CREATE TABLE IF NOT EXISTS task_statistics (" +
            "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
            "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'," +
            "player_uuid TEXT NOT NULL UNIQUE COMMENT '玩家UUID'," +
            "cycle_task_count INTEGER NOT NULL DEFAULT 0 COMMENT '循环任务完成总次数'," +
            "forever_task_count INTEGER NOT NULL DEFAULT 0 COMMENT '永久任务完成总次数'," +
            "limit_task_count INTEGER NOT NULL DEFAULT 0 COMMENT '限时任务完成总次数'," +
            "task_craft_times INTEGER NOT NULL DEFAULT 0 COMMENT '合成任务完成次数'," +
            "task_kill_times INTEGER NOT NULL DEFAULT 0 COMMENT '击杀任务完成次数'," +
            "task_break_times INTEGER NOT NULL DEFAULT 0 COMMENT '破坏任务完成次数'," +
            "task_enchant_times INTEGER NOT NULL DEFAULT 0 COMMENT '附魔任务完成次数'," +
            "task_fishing_times INTEGER NOT NULL DEFAULT 0 COMMENT '钓鱼任务完成次数'," +
            "task_scissor_times INTEGER NOT NULL DEFAULT 0 COMMENT '剪刀任务完成次数'," +
            "task_breed_times INTEGER NOT NULL DEFAULT 0 COMMENT '繁殖任务完成次数'," +
            "task_tame_times INTEGER NOT NULL DEFAULT 0 COMMENT '驯服任务完成次数'," +
            "task_consume_times INTEGER NOT NULL DEFAULT 0 COMMENT '食物任务完成次数'," +
            "task_trigger_times INTEGER NOT NULL DEFAULT 0 COMMENT '拉杆任务完成次数'," +
            "task_drop_times INTEGER NOT NULL DEFAULT 0 COMMENT '丢弃任务完成次数'," +
            "task_take_times INTEGER NOT NULL DEFAULT 0 COMMENT '拾取任务完成次数'," +
            "total_task_count INTEGER NOT NULL DEFAULT 0 COMMENT '总任务完成次数'" +
            ");";

    public static final String mysql_players_tasks_sql = "CREATE TABLE IF NOT EXISTS players_tasks (" +
            "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
            "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'," +
            "player_uuid TEXT NOT NULL COMMENT '玩家UUID'," +
            "task_id TEXT NOT NULL COMMENT '任务ID'," +
            "task_type ENUM('FOREVER', 'LIMIT', 'CYCLE') NOT NULL DEFAULT 'FOREVER' COMMENT '任务类型'," +
            "task_target ENUM('CRAFT', 'KILL', 'BREAK', 'ENCHANT', 'FISHING', 'SCISSOR', 'BREED', 'TAME', 'CONSUME', 'TRIGGER', 'DROP', 'TAKE', 'NONE') NOT NULL DEFAULT 'NONE' COMMENT '任务目标'," +
            "task_progress INTEGER NOT NULL DEFAULT 0 COMMENT '任务进度'," +
            "task_status ENUM('UN_STARTED', 'IN_PROGRESS', 'COMPLETED', 'UN_COMPLETED', 'FAILED') NOT NULL DEFAULT 'UN_STARTED' COMMENT '任务状态'," +
            "accept_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务接受时间'," +
            "finish_at DATETIME COMMENT '任务完成时间，未完成为 NULL'," +
            "expire_at DATETIME COMMENT '任务过期时间，永久任务为 NULL'," +
            ");";
}
