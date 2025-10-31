package com.playerPlugin.playerTaskX.dataManager;

import java.io.File;
import java.sql.SQLException;
import java.util.UUID;

public interface Storge {
    void connect(File dataFolder) throws SQLException, ClassNotFoundException;

    void initDatabase()  throws SQLException;

    void createNewPlayer(UUID uuid)  throws SQLException;

    void close();
}
