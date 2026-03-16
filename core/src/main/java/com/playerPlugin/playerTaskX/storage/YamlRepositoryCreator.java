package com.playerPlugin.playerTaskX.storage;

import com.playerPlugin.playerTaskX.api.storage.RepositoryCreator;

import java.sql.Connection;
import java.sql.SQLException;

public class YamlRepositoryCreator implements RepositoryCreator {
    @Override
    public void connect() throws SQLException, ClassNotFoundException {
        // pass
    }

    @Override
    public void createTables() throws SQLException {
        // pass
    }

    @Override
    public Connection getConnection() {
        return null;
    }

    @Override
    public void close() {
        // pass
    }
}
