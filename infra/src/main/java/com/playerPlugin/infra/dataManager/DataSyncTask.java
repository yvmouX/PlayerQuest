package com.playerPlugin.infra.dataManager;

import cn.yvmou.ylib.api.scheduler.UniversalTask;
import cn.yvmou.ylib.impl.scheduler.UniversalRunnable;
import com.playerPlugin.core.domain.Task.TaskProgress;
import com.playerPlugin.core.domain.Task.TaskTarget;
import com.playerPlugin.infra.cache.DatabaseDAO;
import com.playerPlugin.infra.cache.ProgressCache;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.*;

import static com.playerPlugin.core.utils.Help.*;

public class DataSyncTask extends UniversalRunnable {
    private static final int MAX_CACHE_SIZE = 100;

    private final JavaPlugin plugin;
    private final ProgressCache progressCache;
    private final DatabaseDAO databaseDAO;

    public DataSyncTask(JavaPlugin plugin, ProgressCache playerTaskCache, DatabaseDAO databaseDAO) {
        this.plugin = plugin;
        this.progressCache = playerTaskCache;
        this.databaseDAO = databaseDAO;
        playerTaskCache.init(c());
    }

    private Map<UUID, List<TaskProgress>> c() {
        Map<UUID, List<TaskProgress>> a;
        a = Collections.synchronizedMap(
                new LinkedHashMap<UUID, List<TaskProgress>>(
                        MAX_CACHE_SIZE, // 初始容量 100个玩家 TODO 配置文件自定义
                        0.75f,
                        true
                ) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<UUID, List<TaskProgress>> eldest) {
                        // 当缓存超过最大容量时，移除最久未使用的条目
                        // 但如果是脏数据，先保存到数据库
                        if (size() > MAX_CACHE_SIZE) {
                            if (progressCache.getDirtyEntries().contains(eldest.getKey())) {
                                saveCacheToDatabase(eldest.getValue(), false);
                                progressCache.getDirtyEntries().remove(eldest.getKey());
                            }
                            return true;
                        }
                        return false;
                    }
                });
        return a;
    }

    private final List<TaskProgress> finished = new ArrayList<>();
    private final List<UniversalTask> universalTaskList = new ArrayList<>();

    @Override
    public void run() {
        syncCacheToDatabase();
        syncProgressCacheToDatabase();
        clearCache();
    }

    public void startSync() {
        this.runTimerAsync(10 * 20, 10 * 20);
    }

    public void stopSync() {
        Set<UUID> dirty = progressCache.getDirtyEntries();
        List<TaskProgress> allDirty = new ArrayList<>();

        for (UUID uuid : progressCache.getDirtyEntries()) {
            List<TaskProgress> taskProgressList = progressCache.getCache().get(uuid);
            if (taskProgressList != null) {
                allDirty.addAll(taskProgressList);
            }
        }

        if (!allDirty.isEmpty()) {
            saveCacheToDatabase(allDirty, false);
        }
        dirty.clear();

        universalTaskList.forEach(UniversalTask::cancel);
        universalTaskList.clear();
    }

    /**
     * 将缓存同步到数据库(30s) TODO 配置文件自定义间隔
     *
     * <p>
     *     1. 从缓存中获取脏数据集合
     *     2. 复制一份脏数据集合和缓存数据
     *     3. 异步保存到数据库
     *     4. 从脏数据集合中移除已保存的数据
     * </p>
     */
    private void syncCacheToDatabase() {
        UniversalTask u1 = scheduler.runTimer(() -> {
            Set<UUID> dirty = progressCache.getDirtyEntries();

            if (dirty.isEmpty()) {
                log.debug("没有脏数据需要同步");
                return;
            }

            Set<UUID> copyDirty = new HashSet<>(dirty);
            Map<UUID, List<TaskProgress>> copyCache = progressCache.getCache();

            scheduler.runAsync(() -> {
                for (UUID uuid : copyDirty) {
                    List<TaskProgress> taskProgressList = copyCache.get(uuid);
                    if (taskProgressList != null) {
                        // 保存到数据库
                        saveCacheToDatabase(taskProgressList, false);
                        // 从脏数据集合中移除
                        dirty.remove(uuid);
                    }
                }
            });
        }, 30 * 20, 30 * 20);
        universalTaskList.add(u1);
    }

    /**
     * 定时清理缓存（20s）
     *
     */
    private void clearCache() {
        UniversalTask u2 = scheduler.runTimer(() -> {
            Map<UUID, List<TaskProgress>> cache = progressCache.getCache();
            Set<UUID> dirty = progressCache.getDirtyEntries();

            Set<UUID> cachedPlayers = new HashSet<>(cache.keySet());

            for (UUID uuid : cachedPlayers) {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p == null || !p.isOnline()) {
                    if (dirty.contains(uuid)) {
                        saveCacheToDatabase(cache.get(uuid), false);
                        dirty.remove(uuid);
                    }
                }
                cache.remove(uuid);
            }
        }, 20 * 20, 20 * 20);
        universalTaskList.add(u2);
    }

    /**
     * 将进度缓存同步到数据库
     *
     */
    private void syncProgressCacheToDatabase() {
        filterFinishedTasks();
        for (TaskProgress taskProgress : finished) {
            databaseDAO.updateProgress(List.of(taskProgress));
            finished.remove(taskProgress);
            log.debug(String.format("玩家 %s 任务 %s 进度已同步到数据库", taskProgress.getUUID(), taskProgress.getTask().getId()));
        }
    }


    private void filterFinishedTasks() {
        finished.clear();
        Set<TaskProgress> taskProgressSet = new HashSet<>(progressCache.getMaybeFinishedPlayers());

        for (TaskProgress taskProgress : taskProgressSet) {
            boolean allFinished = true;
            for (TaskTarget t : taskProgress.getTask().getTargets()) {
                if (t.isFinished()) {
                    continue;
                }
                allFinished = false;
                break;
            }
            if (allFinished) {
                finished.add(taskProgress);
            }
        }

        // 清空 maybeFinishedPlayers 缓存，因为已完成的任务已筛选到 finished 中
        progressCache.getMaybeFinishedPlayers().clear();
    }

    private void saveCacheToDatabase(@Nonnull List<TaskProgress> taskProgressList, boolean firstSave) {
        if (taskProgressList.isEmpty()) return;

        if (firstSave) {
            try {
                databaseDAO.startTask(taskProgressList);
                databaseDAO.setProgress(taskProgressList);
                log.info("批量保存玩家任务到数据库成功，任务数量：" + taskProgressList.size());
            } catch (SQLException e) {
                log.error("批量保存玩家任务到数据库失败", e);
            }
        } else {
            try {
                databaseDAO.updateTasks(taskProgressList);
                databaseDAO.updateProgress(taskProgressList);
                log.debug("批量保存玩家任务到数据库成功，任务数量：" + taskProgressList.size());
            } catch (SQLException e) {
                log.error("批量保存玩家任务到数据库失败", e);
            }
        }
    }
}
