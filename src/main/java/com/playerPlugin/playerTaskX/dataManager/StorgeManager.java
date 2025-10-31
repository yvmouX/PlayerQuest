package com.playerPlugin.playerTaskX.dataManager;
import com.playerPlugin.playerTaskX.PlayerTask.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.dataManager.impl.SQLiteManager;

import java.sql.SQLException;

public class StorgeManager {
    private static volatile StorgeManager instance;
    private final PlayerTaskX plugin;
    private final StorgeTypes type;
    private final SQLiteManager sqLiteManager;

    public StorgeManager(PlayerTaskX plugin, StorgeTypes storge, SQLiteManager sqLiteManager) {
        if (instance != null) {
            throw new IllegalStateException("StorgeManger already instantiated");
        }
        this.plugin = plugin;
        this.type = storge;
        this.sqLiteManager = sqLiteManager;
    }

    public static void init(PlayerTaskX plugin, StorgeTypes storge, SQLiteManager sqLiteManager) {
        if (plugin == null) {
            throw new NullPointerException("plugin can't be null");
        }
        if (storge == null) {
            throw new NullPointerException("storge can't be null");
        }
        if (sqLiteManager == null) {
            throw new NullPointerException("sqLiteManager can't be null");
        }

        if (instance == null) {
            synchronized (StorgeManager.class) {
                if (instance == null) {
                    instance = new StorgeManager(plugin, storge, sqLiteManager);
                }
            }
        }
    }

    public static StorgeManager getInstance() {
        if (instance == null) {
            synchronized (StorgeManager.class) {
                if (instance == null) {
                    throw  new NullPointerException("instance can't be null");
                }
            }
        }
        return instance;
    }

    public void connect() {
        switch (type) {
            case SQLITE -> {
                // 数据库
                try {
                    sqLiteManager.connect(plugin.getDataFolder());
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

    public void createTable() {
        switch (type) {
            case SQLITE -> {
                try {
                    sqLiteManager.initDatabase();
                    PlayerTaskX.getYLib().getLoggerTools().info("成功创建 SQLite 数据表！");
                } catch (SQLException e){
                    PlayerTaskX.getYLib().getLoggerTools().error("创建 SQLite 数据表 失败" + e.getMessage());
                    PlayerTaskX.getYLib().getLoggerTools().error("插件已禁用！");
                    plugin.getServer().getPluginManager().disablePlugin(plugin);
                }

            }
            case MYSQL -> {
                // TODO
            }
        }
    }

    public void createNewPlayer(PlayerTask task) {
        switch (type) {
            case SQLITE -> {
                try {
                    sqLiteManager.createNewPlayer(task);
                } catch (SQLException e){
                    PlayerTaskX.getYLib().getLoggerTools().error("创建玩家数据失败" + e.getMessage());
                }
            }
            case MYSQL -> {
                // TODO
            }
        }
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
