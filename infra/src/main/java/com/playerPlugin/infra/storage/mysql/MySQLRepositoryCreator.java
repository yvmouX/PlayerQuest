package com.playerPlugin.infra.storage.mysql;

import com.playerPlugin.core.repository.RepositoryCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;

public class MySQLRepositoryCreator implements RepositoryCreator {
    @Override
    public void connect(JavaPlugin plugin) throws SQLException, ClassNotFoundException {

    }

    @Override
    public void createTables() throws SQLException {

    }

    @Override
    public Connection getConnection() {
        return null;
    }

    @Override
    public void close() {

    }
}
