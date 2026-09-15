package com.playerPlugin.playerTaskX.core.storage.jdbc;

import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import com.playerPlugin.playerTaskX.core.storage.Database;
import com.playerPlugin.playerTaskX.core.storage.Dialect;
import com.playerPlugin.playerTaskX.core.storage.RowMapper;
import com.playerPlugin.playerTaskX.core.storage.StorageException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 JDBC 的存储引擎：SQLite 与 MySQL 共用这一份执行逻辑，差异全在 {@link Dialect} 与「连接从哪来」。
 * SQLite 单连接长驻（写入全局串行，用连接池反而制造 {@code SQLITE_BUSY}），MySQL 走 HikariCP 池化，用完必须归还。
 */
public final class JdbcDatabase implements Database, AutoCloseable {

    /** 连接来源：SQLite 是长驻单连接（归还为空操作），MySQL 是池。 */
    private interface Connections {
        Connection acquire();

        void release(Connection connection);
    }

    private final Connections connections;
    private final Dialect dialect;
    private final Runnable closer;

    private JdbcDatabase(Connections connections, Dialect dialect, Runnable closer) {
        this.connections = connections;
        this.dialect = dialect;
        this.closer = closer;
    }

    // ------------------------------------------------------------------
    // 打开
    // ------------------------------------------------------------------

    /**
     * 打开（必要时创建）SQLite 数据库文件；父目录会被自动创建。
     *
     * @throws SQLException 目录不可创建或连接失败
     */
    public static JdbcDatabase sqlite(File file) throws SQLException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("无法创建数据目录: " + parent.getAbsolutePath());
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        connection.setAutoCommit(true);
        try (Statement statement = connection.createStatement()) {
            // WAL 让读不阻塞写；busy_timeout 避免偶发并发直接报错
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
        }
        return single(connection);
    }

    /** 内存库，供单元测试使用（内存库没有文件，WAL 无意义）。 */
    public static JdbcDatabase sqliteInMemory() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        connection.setAutoCommit(true);
        return single(connection);
    }

    private static JdbcDatabase single(Connection connection) {
        return new JdbcDatabase(new Connections() {
            @Override
            public Connection acquire() {
                return connection;
            }

            @Override
            public void release(Connection ignored) {
                // 单连接长驻，不归还
            }
        }, Dialect.SQLITE, () -> {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // 关闭失败无需处理
            }
        });
    }

    /**
     * 连接 MySQL（HikariCP 连接池）。
     *
     * @param poolName 连接池名，会出现在 Hikari 的线程名与日志里，便于定位
     */
    public static JdbcDatabase mysql(PluginConfig.MysqlSettings settings, String poolName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.getUsername());
        config.setPassword(settings.getPassword());
        config.setMaximumPoolSize(settings.getPoolSize());
        config.setPoolName(poolName);
        config.setConnectionTimeout(10_000L);
        // 插件卸载时若仍有连接占用，不应阻塞关服
        config.setInitializationFailTimeout(5_000L);
        HikariDataSource dataSource = new HikariDataSource(config);

        return new JdbcDatabase(new Connections() {
            @Override
            public Connection acquire() {
                try {
                    return dataSource.getConnection();
                } catch (SQLException e) {
                    throw new StorageException("获取数据库连接失败", e);
                }
            }

            @Override
            public void release(Connection connection) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // 归还失败由连接池自行回收
                }
            }
        }, Dialect.MYSQL, dataSource::close);
    }

    // ------------------------------------------------------------------
    // Database
    // ------------------------------------------------------------------

    @Override
    public void execute(String sql, Object... params) {
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Sql.bind(statement, params);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("执行失败: " + sql, e);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public void executeInline(String sql) {
        Sql.ensureSingleStatement(sql);
        Connection connection = connections.acquire();
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new StorageException("执行失败: " + sql, e);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Sql.bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new StorageException("查询失败: " + sql, e);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public void transaction(Runnable work) {
        Connection connection = connections.acquire();
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            connections.release(connection);
            throw new StorageException("开启事务失败", e);
        }
        try {
            work.run();
            connection.commit();
        } catch (RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } catch (SQLException e) {
            throw new StorageException("提交事务失败", e);
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {
                // 恢复自动提交失败不影响业务
            }
            connections.release(connection);
        }
    }

    @Override
    public Dialect dialect() {
        return dialect;
    }

    /** 关闭底层连接资源：SQLite 关连接，MySQL 关连接池。 */
    @Override
    public void close() {
        closer.run();
    }
}
