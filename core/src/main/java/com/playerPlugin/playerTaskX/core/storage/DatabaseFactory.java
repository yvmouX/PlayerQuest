package com.playerPlugin.playerTaskX.core.storage;

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

    private DatabaseFactory() {
    }

    /**
     * 按配置打开数据库、建表，并返回持有底层资源的句柄。
     *
     * @param config     插件配置，为 null 时按 SQLite + 默认文件名处理
     * @param dataFolder 插件数据目录，SQLite 文件相对它解析
     * @throws StorageException 打开连接或建表失败
     */
    public static Handle open(PluginConfig config, File dataFolder) {
        String type = config == null ? null : config.getStorageType();
        if (type != null && "MYSQL".equalsIgnoreCase(type.trim())) {
            return openMysql(config);
        }
        if (type != null && !type.isBlank() && !"SQLITE".equalsIgnoreCase(type.trim())) {
            System.err.println("[PlayerTaskX] 未知的存储类型 '" + type + "'，已回退 SQLite");
        }
        return openSqlite(config, dataFolder);
    }

    /** SQLite：单连接长驻，连接与建表都由这里负责收尾。 */
    private static Handle openSqlite(PluginConfig config, File dataFolder) {
        String fileName = config == null ? null : config.getSqliteFile();
        if (fileName == null || fileName.isBlank()) {
            fileName = DEFAULT_SQLITE_FILE;
        }
        File folder = dataFolder == null ? new File(".") : dataFolder;
        File file = new File(folder, fileName);

        SqliteDatabase sqlite;
        try {
            // 父目录由 SqliteDatabase.open 负责创建
            sqlite = SqliteDatabase.open(file);
        } catch (SQLException e) {
            throw new StorageException("打开 SQLite 数据库失败: " + file.getAbsolutePath(), e);
        }
        Database database = sqlite.database();
        try {
            Schema.initialize(database);
        } catch (RuntimeException e) {
            sqlite.close();
            throw e;
        }
        // 描述里用配置的原始相对路径，比绝对路径更适合直接展示给服主
        return new Handle(sqlite, database, "SQLite: " + fileName);
    }

    /** MySQL：Hikari 连接池，失败时统一包装成 {@link StorageException}。 */
    private static Handle openMysql(PluginConfig config) {
        PluginConfig.MysqlSettings settings = config.getMysql();
        MysqlDatabase mysql;
        try {
            mysql = MysqlDatabase.open(settings, MYSQL_POOL_NAME);
        } catch (RuntimeException e) {
            throw new StorageException("连接 MySQL 失败: " + describeMysql(settings), e);
        }
        Database database = mysql.database();
        try {
            Schema.initialize(database);
        } catch (RuntimeException e) {
            mysql.close();
            throw e;
        }
        return new Handle(mysql, database, "MySQL: " + describeMysql(settings));
    }

    /** {@code host:port/database} 形式的可读描述。 */
    private static String describeMysql(PluginConfig.MysqlSettings settings) {
        return settings.getHost() + ":" + settings.getPort() + "/" + settings.getDatabase();
    }

    /**
     * 数据库句柄：把「门面 + 底层资源 + 描述」绑在一起，调用方只依赖 {@link Database}，
     * 关闭时由这里统一兜底，避免 SQLite 连接与 Hikari 池被漏关。
     */
    public static final class Handle implements AutoCloseable {

        private final AutoCloseable resources;
        private final Database database;
        private final String description;
        private boolean closed;

        private Handle(AutoCloseable resources, Database database, String description) {
            this.resources = resources;
            this.database = database;
            this.description = description;
        }

        /** 数据库门面，仓储都基于它构造。 */
        public Database database() {
            return database;
        }

        /**
         * 任务定义仓储。
         * <p>
         * 仓储实例是无状态的（只持有 {@link Database}），因此按需构造即可，
         * 不必在工厂里持有字段；这样接入新方言时也无需改动这里。
         */
        public QuestRepository questRepository() {
            return new JdbcQuestRepository(database);
        }

        /** 玩家任务仓储。 */
        public PlayerQuestRepository playerQuestRepository() {
            return new JdbcPlayerQuestRepository(database);
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
                resources.close();
            } catch (Exception e) {
                // 关闭失败不该阻断插件卸载流程，只记录
                System.err.println("[PlayerTaskX] 关闭数据库资源失败: " + description + " (" + e + ")");
            }
        }
    }
}
