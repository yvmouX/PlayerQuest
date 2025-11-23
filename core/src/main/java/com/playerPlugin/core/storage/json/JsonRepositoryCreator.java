package com.playerPlugin.core.storage.json;

import com.playerPlugin.core.storage.RepositoryCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;

public class JsonRepositoryCreator implements RepositoryCreator {
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
