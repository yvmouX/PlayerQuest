package com.playerPlugin.playerTaskX.core.storage.jdbc;

import com.playerPlugin.playerTaskX.core.storage.Dialect;
import com.playerPlugin.playerTaskX.core.storage.RowMapper;
import com.playerPlugin.playerTaskX.core.storage.Database;
import com.playerPlugin.playerTaskX.core.storage.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 JDBC 的通用实现：两种数据库只差连接来源与方言。
 * <p>
 * 连接的生命周期完全交给 {@link ConnectionProvider}，这里不做「猜」——
 * SQLite 是长驻单连接（不能关），MySQL 是池化连接（用完必须归还）。
 */
public final class JdbcDatabase implements Database {

    /** 连接提供者，负责借出与归还。 */
    public interface ConnectionProvider {
        Connection acquire();

        /** 归还连接；长驻连接实现为空操作。 */
        void release(Connection connection);
    }

    private final ConnectionProvider provider;
    private final Dialect dialect;

    public JdbcDatabase(ConnectionProvider provider, Dialect dialect) {
        this.provider = provider;
        this.dialect = dialect;
    }

    /** 便捷工厂：单连接长驻（SQLite）。 */
    public static JdbcDatabase singleConnection(Connection connection, Dialect dialect) {
        return new JdbcDatabase(new ConnectionProvider() {
            @Override
            public Connection acquire() {
                return connection;
            }

            @Override
            public void release(Connection ignored) {
                // 单连接长驻，不归还
            }
        }, dialect);
    }

    @Override
    public void execute(String sql, Object... params) {
        Connection connection = provider.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Sql.bind(statement, params);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("执行失败: " + sql, e);
        } finally {
            provider.release(connection);
        }
    }

    @Override
    public void executeInline(String sql) {
        Sql.ensureSingleStatement(sql);
        Connection connection = provider.acquire();
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new StorageException("执行失败: " + sql, e);
        } finally {
            provider.release(connection);
        }
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        Connection connection = provider.acquire();
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
            provider.release(connection);
        }
    }

    @Override
    public void transaction(Runnable work) {
        Connection connection = provider.acquire();
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            provider.release(connection);
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
            provider.release(connection);
        }
    }

    @Override
    public Dialect dialect() {
        return dialect;
    }

    /**
     * 关闭门面。
     * <p>
     * 这里只标记状态，不关闭连接——连接的归属在 {@link ConnectionProvider}：
     * SQLite 是长驻单连接（由 {@code SqliteDatabase} 关），
     * MySQL 是池化连接（由 {@code MysqlDatabase} 关数据源）。
     * 若在这里关连接，会把上层仍在使用的连接一起关掉。
     */
    @Override
    public void close() {
        // 由持有资源的实现负责关闭，见上方说明
    }
}
