package com.playerPlugin.playerTaskX.dataManager;

import com.playerPlugin.playerTaskX.PlayerTask.PlayerTask;

import java.io.File;
import java.sql.SQLException;

public interface Storge {
    void connect(File dataFolder) throws SQLException, ClassNotFoundException;

    void initDatabase()  throws SQLException;

    void createNewPlayer(PlayerTask task)  throws SQLException;

    void close();
}
