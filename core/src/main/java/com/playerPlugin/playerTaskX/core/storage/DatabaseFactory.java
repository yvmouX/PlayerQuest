package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcDatabase;
import com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcPlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcPresetRepository;
import com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.jdbc.Schema;

import com.playerPlugin.playerTaskX.core.config.PluginConfig;

import java.io.File;
import java.sql.SQLException;

/**
 * 按配置打开数据库、建表并装配四份仓储，是存储层唯一的入口；三种数据共用一个库，SQLite 与 MySQL 的差异全在 {@link Dialect}。
 * 未知存储类型回退 SQLite（写错一个单词不该让插件起不来）；建表失败必须关掉已打开的资源，否则句柄/连接池会泄漏。
 */
public final class DatabaseFactory {

    /** SQLite 默认文件名，与 {@link PluginConfig} 的默认值保持一致。 */
    private static final String DEFAULT_SQLITE_FILE = "data/playerTaskX.db";

    /** MySQL 连接池名，出现在 Hikari 的线程名与日志里，便于定位。 */
    private static final String MYSQL_POOL_NAME = "playerTaskX";

    private DatabaseFactory() {
    }

    /** 按配置打开存储并建表（未知类型回退 SQLite）；失败抛 {@link StorageException}，由插件禁用自己。 */
    public static Handle open(PluginConfig config, File dataFolder) {
        String type = config == null ? null : config.getStorageType();
        String normalized = type == null ? "" : type.trim().toUpperCase(java.util.Locale.ROOT);

        if ("MYSQL".equals(normalized)) {
            return openMysql(config);
        }
        if (!normalized.isEmpty() && !"SQLITE".equals(normalized)) {
            System.err.println("[PlayerTaskX] 未知的存储类型 '" + type + "'，已回退 SQLite");
        }
        return openSqlite(config, dataFolder);
    }

    /** SQLite：单连接长驻，建表失败时把连接关掉再抛出。 */
    private static Handle openSqlite(PluginConfig config, File dataFolder) {
        String fileName = config == null ? null : config.getSqliteFile();
        if (fileName == null || fileName.isBlank()) {
            fileName = DEFAULT_SQLITE_FILE;
        }
        File folder = dataFolder == null ? new File(".") : dataFolder;
        File file = new File(folder, fileName);

        JdbcDatabase sqlite;
        try {
            // 父目录由 JdbcDatabase.sqlite 负责创建
            sqlite = JdbcDatabase.sqlite(file);
        } catch (SQLException e) {
            throw new StorageException("打开 SQLite 数据库失败: " + file.getAbsolutePath(), e);
        }
        initialize(sqlite);
        // 描述里用配置的原始相对路径，比绝对路径更适合直接展示给服主
        return new Handle(sqlite, "SQLite: " + fileName);
    }

    /** MySQL：Hikari 连接池，失败时统一包装成 {@link StorageException}。 */
    private static Handle openMysql(PluginConfig config) {
        PluginConfig.MysqlSettings settings = config.getMysql();
        JdbcDatabase mysql;
        try {
            mysql = JdbcDatabase.mysql(settings, MYSQL_POOL_NAME);
        } catch (RuntimeException e) {
            throw new StorageException("连接 MySQL 失败: " + describeMysql(settings), e);
        }
        initialize(mysql);
        return new Handle(mysql, "MySQL: " + describeMysql(settings));
    }

    /** 建表；失败时关闭刚打开的资源再抛出，避免句柄/连接池泄漏。 */
    private static void initialize(JdbcDatabase database) {
        try {
            Schema.initialize(database);
        } catch (RuntimeException e) {
            database.close();
            throw e;
        }
    }

    /** {@code host:port/database} 形式的可读描述。 */
    private static String describeMysql(PluginConfig.MysqlSettings settings) {
        return settings.getHost() + ":" + settings.getPort() + "/" + settings.getDatabase();
    }

    /** 数据库句柄：把门面与四份仓储绑在一起，调用方只依赖各仓储接口；底层资源统一由 {@link JdbcDatabase#close()} 关闭，这里不再单独持有。 */
    public static final class Handle implements AutoCloseable {

        private final JdbcDatabase database;
        private final QuestRepository quests;
        private final PresetRepository presets;
        private final PlayerQuestRepository playerQuestRepository;
        private final String description;
        private boolean closed;

        private Handle(JdbcDatabase database, String description) {
            this.database = database;
            this.quests = new JdbcQuestRepository(database);
            this.presets = new JdbcPresetRepository(database);
            this.playerQuestRepository = new JdbcPlayerQuestRepository(database);
            this.description = description;
        }

        /** 任务定义仓储（游戏内指令与 GUI 的定义读写都走它）。 */
        public QuestRepository quests() {
            return quests;
        }

        /** 预设仓储；目标/奖励预设与任务定义同库。 */
        public PresetRepository presets() {
            return presets;
        }

        /** 玩家数据仓储：进度、状态与每日刷新次数。 */
        public PlayerQuestRepository playerQuestRepository() {
            return playerQuestRepository;
        }

        /** 人类可读描述，如 {@code SQLite: data/playerTaskX.db}。 */
        public String description() {
            return description;
        }

        /** 幂等关闭：重复调用无副作用。 */
        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                database.close();
            } catch (Exception e) {
                // 关闭失败不该阻断插件卸载流程，只记录
                System.err.println("[PlayerTaskX] 关闭数据库资源失败: " + description + " (" + e + ")");
            }
        }
    }
}
