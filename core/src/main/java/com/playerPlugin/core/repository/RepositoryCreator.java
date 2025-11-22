package com.playerPlugin.core.repository;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;

public interface RepositoryCreator {
    void connect(JavaPlugin plugin) throws SQLException, ClassNotFoundException;

    void createTables() throws SQLException;

    Connection getConnection();

    void close();
}
