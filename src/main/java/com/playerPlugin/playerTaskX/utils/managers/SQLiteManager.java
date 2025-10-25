package com.playerPlugin.playerTaskX.utils.managers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;

public class SQLiteManager {
    private Connection conn;

    public void connectToSQLite() {
        try {
            Class.forName("org.sqlite.JDBC");

            String url = "jdbc:sqlite:data.db";
            conn = DriverManager.getConnection(url);

        } catch (ClassNotFoundException | SQLException e) {
            log.err("Failed to connect to SQLite database: " + e.getMessage());
        }
    }

    public void createTable() {
        String players_sql = "CREATE TABLE IF NOT EXISTS players (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_name TEXT NOT NULL," +
                "task_id TEXT NOT NULL," +
                "finish BOOLEAN DEFAULT FALSE)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(players_sql);
            System.out.println("Table created successfully");
        } catch (SQLException e) {
            log.err("Failed to create table: " + e.getMessage());
        }
    }
}
