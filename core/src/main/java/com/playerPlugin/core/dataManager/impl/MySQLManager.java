//package com.playerPlugin.playerTaskX.dataManager.impl;
//
//import com.playerPlugin.playerTaskX.domain.PlayerTask.Enum.PlayerTaskStatus;
//import com.playerPlugin.playerTaskX.domain.PlayerTask.TaskDefinition.PlayerTask;
//import com.playerPlugin.playerTaskX.dataManager.Storge;
//
//import java.io.File;
//import java.sql.Connection;
//import java.sql.DriverManager;
//import java.sql.SQLException;
//import java.sql.Statement;
//import java.time.ZoneId;
//import java.time.ZonedDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.Properties;
//import java.util.UUID;
//
//import static com.playerPlugin.playerTaskX.PlayerTaskX.log;
//import static com.playerPlugin.playerTaskX.dataManager.sql.MysqlSQL.mysql_players_tasks_sql;
//import static com.playerPlugin.playerTaskX.dataManager.sql.MysqlSQL.mysql_task_statistics_sql;
//
//public class MySQLManager implements Storge {
//    private Connection conn;
//    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
//    private final String host;
//    private final int port;
//    private final String database;
//    private final String username;
//    private final String password;
//
//    private static String getCurrentTime() {
//        return ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(formatter);
//    }
//
//    public MySQLManager(String host, int port, String database, String username, String password) {
//        this.host = host;
//        this.port = port;
//        this.database = database;
//        this.username = username;
//        this.password = password;
//    }
//
//    @Override
//    public void connect(File dataFolder) throws SQLException, ClassNotFoundException {
//        Class.forName("com.mysql.cj.jdbc.Driver");
//        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
//                host, port, database);
//        Properties props = new Properties();
//        props.setProperty("user", username);
//        props.setProperty("password", password);
//
//        this.conn = DriverManager.getConnection(url, props);
//
//    }
//
//    @Override
//    public void initDatabase() throws SQLException {
//        if (conn == null || conn.isClosed()) {
//            throw new SQLException("Database connection is not established");
//        }
//
//        // 建表
//        for (String sql : new String[]{mysql_task_statistics_sql, mysql_players_tasks_sql}) {
//            try (Statement stmt = conn.createStatement()) {
//                stmt.execute(sql);
//            }
//        }
//    }
//
//    @Override
//    public void createNewPlayer(PlayerTask task) throws SQLException {
//        if (conn == null || conn.isClosed()) {
//            throw new SQLException("Connection is not open. Call connect() first.");
//        }
//        String createdAt = getCurrentTime();
//        UUID playerUuid = task.getUUID();
//        String taskId = task.getTask().getId();
//        String taskStatus = PlayerTaskStatus.IN_PROGRESS.toString();
//
//
//    }
//
//    @Override
//    public void close() {
//        if (conn != null) {
//            try {
//                if (!conn.isClosed()) {
//                    conn.close();
//                }
//            } catch (SQLException e) {
//                log.err("Error closing database connection: " + e.getMessage());
//            }
//        }
//    }
//}
