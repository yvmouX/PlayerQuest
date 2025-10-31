package com.playerPlugin.playerTaskX.dataManager;
import com.playerPlugin.playerTaskX.PlayerTaskX;

import java.sql.SQLException;
import java.util.UUID;

public class StorgeManager {
    private final PlayerTaskX plugin;
    private final StorgeTypes type;
    private final SQLiteManager sqLiteManager;

    public StorgeManager(PlayerTaskX plugin, StorgeTypes storge, SQLiteManager sqLiteManager) {
        this.plugin = plugin;
        this.type = storge;
        this.sqLiteManager = sqLiteManager;
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

    public void createNewPlayer(UUID uuid) {
        switch (type) {
            case SQLITE -> {
                try {
                    sqLiteManager.createNewPlayer(uuid);
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
