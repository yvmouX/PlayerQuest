package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcDatabase;
import com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcPlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.jdbc.Schema;

import com.playerPlugin.playerTaskX.core.config.PluginConfig;

import java.io.File;
import java.sql.SQLException;

/**
 * 按配置打开数据库并完成建表。
 * <p>
 * 关键取舍：
 * <ul>
 *   <li><b>未知存储类型回退 SQLite</b>：配置文件里写错一个单词（如 {@code MYSQL5}）时，
 *       能让插件带着本地库正常起来，比直接启动失败更符合使用者的预期，同时打一条告警。</li>
 *   <li><b>建表失败必须关闭已打开的资源</b>：否则 SQLite 的文件句柄/MySQL 的连接池会泄漏，
 *       在插件热重载场景下会越积越多。</li>
 *   <li><b>{@link Handle} 幂等关闭</b>：插件 onDisable 与异常路径可能重复触发 close。</li>
 * </ul>
 */
public final class DatabaseFactory {

    /** SQLite 默认文件名，与 {@link PluginConfig} 的默认值保持一致。 */
    private static final String DEFAULT_SQLITE_FILE = "data/playerTaskX.db";

    /** MySQL 连接池名，出现在 Hikari 的线程名与日志里，便于定位。 */
    private static final String MYSQL_POOL_NAME = "playerTaskX";

    /** 玩家数据用文件后端时，这个目录存放每个玩家一份 JSON。 */
    private static final String PLAYER_FOLDER = "players";

    private DatabaseFactory() {
    }

    /**
     * 按配置打开存储。
     * <p>
     * 支持三种玩家数据后端：{@code SQLITE}（默认）、{@code MYSQL}（多服共享）、
     * {@code JSON}（单服小规模，完全不依赖数据库）。选 JSON 时不会打开任何数据库连接，
     * {@link Handle#database()} 返回 {@code null}——调用方必须容忍这一点。
     *
     * @param config     插件配置，为 null 时按 SQLite + 默认文件名处理
     * @param dataFolder 插件数据目录，SQLite 文件与玩家目录都相对它解析
     * @throws StorageException 打开连接或建表失败
     */
    public static Handle open(PluginConfig config, File dataFolder) {
        String type = config == null ? null : config.getStorageType();
        String normalized = type == null ? "" : type.trim().toUpperCase(java.util.Locale.ROOT);

        if ("JSON".equals(normalized)) {
            File base = dataFolder == null ? new File(".") : dataFolder;
            return new Handle(null,
                    new JsonPlayerQuestRepository(new File(base, PLAYER_FOLDER).toPath(),
                            System.err::println),
                    "JSON: " + PLAYER_FOLDER + "/");
        }
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
        return new Handle(sqlite, new JdbcPlayerQuestRepository(sqlite), "SQLite: " + fileName);
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
        return new Handle(mysql, new JdbcPlayerQuestRepository(mysql),
                "MySQL: " + describeMysql(settings));
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

    /**
     * 数据库句柄：把「门面 + 描述」绑在一起，调用方只依赖 {@link Database}。
     * <p>
     * 底层资源由 {@link Database#close()} 负责（{@code JdbcDatabase} 自己知道该关连接还是关池），
     * 因此这里不再单独持有 {@code AutoCloseable} —— 两处都能关是泄漏的温床。
     */
    public static final class Handle implements AutoCloseable {

        private final Database database;
        private final PlayerQuestRepository playerQuestRepository;
        private final String description;
        private boolean closed;

        private Handle(Database database, PlayerQuestRepository playerQuestRepository, String description) {
            this.database = database;
            this.playerQuestRepository = playerQuestRepository;
            this.description = description;
        }

        /**
         * 数据库门面；玩家数据用 JSON 文件后端时为 {@code null}。
         * <p>
         * 选 SQL 后端来存任务定义时才有值——两条路径都不该假设它一定存在。
         */
        public Database database() {
            return database;
        }

        /** 玩家数据仓储：SQLite / MySQL / JSON 三选一，由配置决定。 */
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
            if (database == null) {
                return;
            }
            try {
                database.close();
            } catch (Exception e) {
                // 关闭失败不该阻断插件卸载流程，只记录
                System.err.println("[PlayerTaskX] 关闭数据库资源失败: " + description + " (" + e + ")");
            }
        }
    }
}
