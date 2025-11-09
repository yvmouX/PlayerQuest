package com.playerPlugin.playerTaskX.dataManager;

import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.dataManager.cache.PlayerTaskCache;
import com.playerPlugin.playerTaskX.dataManager.dao.PlayerTaskDAO;
import com.playerPlugin.playerTaskX.dataManager.dao.PlayerTaskProgressDAO;
import com.playerPlugin.playerTaskX.dataManager.impl.SQLiteManager;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static com.playerPlugin.playerTaskX.utils.Help.*;

public class StorgeManager {
    private static volatile StorgeManager instance;
    // dao
    private static PlayerTaskDAO playerTaskDAO;
    private static PlayerTaskProgressDAO playerTaskProgressDAO;
    // cache
    private static PlayerTaskCache playerTaskCache;
    // sqlite
    private final SQLiteManager sqLiteManager;
    // other
    private final PlayerTaskX plugin;
    private final StorgeTypes type;

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
                    // 初始化缓存
                    playerTaskCache = new PlayerTaskCache();
                }
            }
        }
    }

    public static StorgeManager getInstance() {
        if (instance == null) {
            logger.error("StorgeManager is not initialized");
        }
        return instance;
    }

    public PlayerTaskDAO getPlayerTaskDAO() {
        if (playerTaskDAO == null) {
            logger.error("PlayerTaskDAO is not initialized");
        }
        return playerTaskDAO;
    }

    public PlayerTaskProgressDAO getPlayerTaskProgressDAO() {
        if (playerTaskProgressDAO == null) {
            logger.error("PlayerTaskProgressDAO is not initialized");
        }
        return playerTaskProgressDAO;
    }

    public PlayerTaskCache getPlayerTaskCache() {
        if (playerTaskCache == null) {
            logger.error("PlayerTaskCache is not initialized");
        }
        return playerTaskCache;
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
                    logger.info("成功连接到 SQLite 数据库！");
                } catch (SQLException | ClassNotFoundException e) {
                    logger.error("连接到 SQLite 数据库失败：" + e.getMessage());
                    logger.error("插件已禁用！");
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

    /**
     * 从数据库加载数据到缓存
     *
     */
    public void databaseToCache(Player p) {
        List<String> inProgressTaskIdList = new LinkedList<>();
        try {
            inProgressTaskIdList = getPlayerTaskDAO().getInProgressTaskIds(p.getUniqueId().toString());
        } catch (SQLException e) {
            logger.error("从数据库获取玩家 " + p.getName() + " 进行中的任务时失败：" + e.getMessage());
        }

        if (inProgressTaskIdList != null) {
            List<String> copy = new LinkedList<>(inProgressTaskIdList);
            scheduler.runAsync(() -> {
                for (String taskID : copy) {
                    sm.getPlayerTaskCache().updatePlayerTaskToCache(List.of(Objects.requireNonNull(tm.toPlayerTask(p.getUniqueId(), taskID))), false);
                }
            });
            logger.debug("已加载玩家 " + p.getName() + " 进行中的任务：" + inProgressTaskIdList + " 共 " + inProgressTaskIdList.size() + " 个");
        }
    }
}
