package com.playerPlugin.playerTaskX.dataManager;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.dataManager.dao.PlayerTaskDAO;
import com.playerPlugin.playerTaskX.dataManager.dao.PlayerTaskProgressDAO;
import com.playerPlugin.playerTaskX.dataManager.impl.SQLiteManager;

import java.sql.Connection;
import java.sql.SQLException;

public class StorgeManager {
    private static volatile StorgeManager instance;
    private final PlayerTaskX plugin;
    private final StorgeTypes type;
    private final SQLiteManager sqLiteManager;
    private static PlayerTaskDAO playerTaskDAO;
    private static PlayerTaskProgressDAO playerTaskProgressDAO;

    public StorgeManager(PlayerTaskX plugin, StorgeTypes storge, SQLiteManager sqLiteManager) {
        if (instance != null) {
            throw new IllegalStateException("StorgeManger already instantiated");
        }
        this.plugin = plugin;
        this.type = storge;
        this.sqLiteManager = sqLiteManager;
    }

    public static void init(PlayerTaskX plugin, StorgeTypes storge, SQLiteManager sqLiteManager) {
        if (instance == null) {
            synchronized (StorgeManager.class) {
                if (instance == null) {
                    instance = new StorgeManager(plugin, storge, sqLiteManager);
                    // 初始化数据库DAO
                    playerTaskProgressDAO = new PlayerTaskProgressDAO(instance);
                    playerTaskDAO = new PlayerTaskDAO(instance);
                }
            }
        }
    }

    public static StorgeManager getInstance() {
        if (instance == null) {
            PlayerTaskX.getYLib().getLoggerTools().error("StorgeManager is not initialized");
        }
        return instance;
    }

    public static PlayerTaskDAO getPlayerTaskDAO() {
        if (playerTaskDAO == null) {
            PlayerTaskX.getYLib().getLoggerTools().error("PlayerTaskDAO is not initialized");
        }
        return playerTaskDAO;
    }

    public static PlayerTaskProgressDAO getPlayerTaskProgressDAO() {
        if (playerTaskProgressDAO == null) {
            PlayerTaskX.getYLib().getLoggerTools().error("PlayerTaskProgressDAO is not initialized");
        }
        return playerTaskProgressDAO;
    }

    /**
     * 连接 / 创建表
     *
     */
    public void connect() {
        switch (type) {
            case SQLITE -> {
                // 数据库
                try {
                    sqLiteManager.connect(plugin);
                    PlayerTaskX.getYLib().getLoggerTools().info("成功连接到 SQLite 数据库！");
                } catch (SQLException | ClassNotFoundException e) {
                    PlayerTaskX.getYLib().getLoggerTools().error("连接到 SQLite 数据库失败：" + e.getMessage());
                    PlayerTaskX.getYLib().getLoggerTools().error("插件已禁用！");
                    plugin.getServer().getPluginManager().disablePlugin(plugin);
                }
            }
            case MYSQL -> {
                // TODO
            }
        }
    }


    public Connection getConnection() {
        switch (type) {
            case SQLITE -> {
                return sqLiteManager.getConnection();
            }
            case MYSQL -> {

            }
        }
        return null;
    }


    public void close() {
        switch (type) {
            case SQLITE -> {
                sqLiteManager.close();
            }
            case MYSQL -> {

            }
        }
    }
}
