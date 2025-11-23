package com.playerPlugin.playerTaskX.storage;

import cn.yvmou.ylib.api.scheduler.UniversalScheduler;
import cn.yvmou.ylib.api.scheduler.UniversalTask;
import cn.yvmou.ylib.impl.scheduler.UniversalRunnable;
import cn.yvmou.ylib.tools.LoggerTools;
import com.playerPlugin.playerTaskX.domain.Task.TaskProgress;
import com.playerPlugin.playerTaskX.domain.Task.TaskTarget;

import java.sql.SQLException;
import java.util.*;

public class DataSyncTask extends UniversalRunnable {
    private final LoggerTools log;
    private final UniversalScheduler scheduler;
    private final TaskCache taskCache;
    private final DatabaseDAO databaseDAO;

    public DataSyncTask(LoggerTools log, UniversalScheduler scheduler, TaskCache taskCache, DatabaseDAO databaseDAO) {
        this.log = log;
        this.scheduler = scheduler;
        this.taskCache = taskCache;
        this.databaseDAO = databaseDAO;
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
        Set<UUID> dirty = taskCache.getDirtyEntries();
        List<TaskProgress> allDirty = new ArrayList<>();

        for (UUID uuid : taskCache.getDirtyEntries()) {
            List<TaskProgress> taskProgressList = taskCache.getCache().get(uuid);
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
            Set<UUID> dirty = taskCache.getDirtyEntries();

            if (dirty.isEmpty()) {
                log.debug("没有脏数据需要同步");
                return;
            }

            Set<UUID> copyDirty = new HashSet<>(dirty);
            Map<UUID, List<TaskProgress>> copyCache = taskCache.getCache();

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
     * 定时清理已经离线的玩家缓存（20s）
     *
     */
    private void clearCache(List<UUID> offlinePlayers) {
        UniversalTask u2 = scheduler.runTimerAsync(() -> {
            if (!offlinePlayers.isEmpty()) {
                Map<UUID, List<TaskProgress>> cache = taskCache.getCache();
                Set<UUID> dirty = taskCache.getDirtyEntries();

                List<UUID> processedPlayers = new ArrayList<>();

                for (Map.Entry<UUID, List<TaskProgress>> entry : cache.entrySet()) {
                    UUID uuid = entry.getKey();

                    // 其实被添加到 dirty 集合中的玩家无非是由于离线导致的，这里额外判断一次是否是离线玩家，是为了保险起见
                    // In fact, the players that are added to the dirty collection are caused by nothing more than being offline, and the extra judgment of whether they are offline players here is just for insurance purposes
                    if (offlinePlayers.contains(uuid) && dirty.contains(uuid)) {
                        saveCacheToDatabase(entry.getValue(), false);

                        cache.remove(uuid);

                        dirty.remove(uuid);

                        processedPlayers.add(uuid);
                    }
                }

                offlinePlayers.removeAll(processedPlayers);
                log.debug(String.format("已清理 %d 个玩家的缓存, 剩余 %d 个玩家", processedPlayers.size(), offlinePlayers.size()));
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
        Set<TaskProgress> taskProgressSet = new HashSet<>(taskCache.getMaybeFinishedPlayers());

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
        taskCache.getMaybeFinishedPlayers().clear();
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
