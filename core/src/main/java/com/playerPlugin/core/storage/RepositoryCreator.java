package com.playerPlugin.core.storage;

import java.sql.Connection;
import java.sql.SQLException;

public interface RepositoryCreator<T> {
    void connect(T dataFolder) throws SQLException, ClassNotFoundException;

    void createTables() throws SQLException;

    Connection getConnection();

    void close();
}
