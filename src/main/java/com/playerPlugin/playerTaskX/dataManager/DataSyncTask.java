package com.playerPlugin.playerTaskX.dataManager;

import cn.yvmou.ylib.impl.scheduler.UniversalRunnable;
import com.playerPlugin.playerTaskX.dataManager.cache.PlayerTaskCache;
import com.playerPlugin.playerTaskX.dataManager.dao.CacheDAO;
import com.playerPlugin.playerTaskX.dataManager.dao.DatabaseDAO;
import org.bukkit.plugin.java.JavaPlugin;

public class DataSyncTask extends UniversalRunnable {
    private final JavaPlugin plugin;
    private final PlayerTaskCache playerTaskCache;
    private final CacheDAO cacheDAO;
    private final DatabaseDAO databaseDAO;

    public DataSyncTask(JavaPlugin plugin, PlayerTaskCache playerTaskCache, CacheDAO cacheDAO, DatabaseDAO databaseDAO) {
        this.plugin = plugin;
        this.playerTaskCache = playerTaskCache;
        this.cacheDAO = cacheDAO;
        this.databaseDAO = databaseDAO;
    }

    @Override
    public void run() {
        // TODO 同步缓存到数据库 从PlayerTaskCache类 转移到这里
    }

    public void startSync() {
        this.runTimerAsync(10 * 20, 10 * 20);
    }
}
