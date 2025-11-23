package com.playerPlugin.playerTaskX.storage.yaml;

import com.playerPlugin.playerTaskX.storage.RepositoryCreator;

import java.sql.Connection;
import java.sql.SQLException;

public class YamlRepositoryCreator implements RepositoryCreator<Void> {
    @Override
    public void connect(Void value) throws SQLException, ClassNotFoundException {
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
