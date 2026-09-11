package com.playerPlugin.playerTaskX.core.storage.jdbc;

import com.playerPlugin.playerTaskX.core.storage.Dialect;
import com.playerPlugin.playerTaskX.core.storage.Database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * SQLite 存储：单连接长驻。
 * <p>
 * SQLite 的写入是全局串行的，用连接池反而会制造 SQLITE_BUSY，
 * 因此这里只保留一个连接，并用 WAL 提升并发读性能。
 */
public final class SqliteDatabase implements AutoCloseable {

    private final Connection connection;
    private final JdbcDatabase database;

    private SqliteDatabase(Connection connection) {
        this.connection = connection;
        this.database = JdbcDatabase.singleConnection(connection, Dialect.SQLITE);
    }

    /**
     * 打开（必要时创建）SQLite 数据库文件。
     *
     * @param file 数据库文件，父目录会被自动创建
     */
    public static SqliteDatabase open(File file) throws SQLException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("无法创建数据目录: " + parent.getAbsolutePath());
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        connection.setAutoCommit(true);
        try (var statement = connection.createStatement()) {
            // WAL 让读不阻塞写；busy_timeout 避免偶发并发直接报错
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
        }
        return new SqliteDatabase(connection);
    }

    /** 内存库，供单元测试使用。 */
    public static SqliteDatabase inMemory() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        connection.setAutoCommit(true);
        return new SqliteDatabase(connection);
    }

    public Database database() {
        return database;
    }

    @Override
    public void close() {
        database.close();
        try {
            connection.close();
        } catch (SQLException ignored) {
            // 关闭失败无需处理
        }
    }
}
