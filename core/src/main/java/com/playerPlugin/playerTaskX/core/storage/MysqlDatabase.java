package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * MySQL 存储：HikariCP 连接池。
 */
public final class MysqlDatabase implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final JdbcDatabase database;

    private MysqlDatabase(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.database = new JdbcDatabase(new JdbcDatabase.ConnectionProvider() {
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
        }, Dialect.MYSQL);
    }

    public static MysqlDatabase open(PluginConfig.MysqlSettings settings, String poolName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.getUsername());
        config.setPassword(settings.getPassword());
        config.setMaximumPoolSize(settings.getPoolSize());
        config.setPoolName(poolName);
        config.setConnectionTimeout(10_000L);
        // 插件卸载时若仍有连接占用，不应阻塞关服
        config.setInitializationFailTimeout(5_000L);
        return new MysqlDatabase(new HikariDataSource(config));
    }

    public Database database() {
        return database;
    }

    @Override
    public void close() {
        database.close();
        dataSource.close();
    }
}
