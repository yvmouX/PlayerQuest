package com.playerPlugin.playerTaskX.dataManager;

import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public interface Storge {
    void connect(JavaPlugin plugin) throws SQLException, ClassNotFoundException;

    Connection getConnection() throws SQLException;

    void close();
}
