package com.playerPlugin.playerTaskX.api.storage;

import java.sql.Connection;
import java.sql.SQLException;

public interface RepositoryCreator {
    void connect() throws SQLException, ClassNotFoundException;

    void createTables() throws SQLException;

    Connection getConnection();

    void close();
}
