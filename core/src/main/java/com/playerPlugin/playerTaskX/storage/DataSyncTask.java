//package com.playerPlugin.playerTaskX.storage;
//
//import cn.yvmou.ylib.api.scheduler.UniversalScheduler;
//import cn.yvmou.ylib.api.scheduler.UniversalTask;
//import cn.yvmou.ylib.tools.LoggerTools;
//import com.playerPlugin.playerTaskX.cache.TaskCache;
//import com.playerPlugin.playerTaskX.domain.Task.TaskProgress;
//import com.playerPlugin.playerTaskX.domain.Task.TaskTarget;
//
//import java.sql.SQLException;
//import java.util.*;
//import java.util.concurrent.ConcurrentLinkedQueue;
//import java.util.concurrent.atomic.AtomicBoolean;
///**
// *
// */
//
///**
// * 更稳健的 DataSyncTask 实现：
// * - 以快照（深拷贝）方式读取 taskCache 数据，所有 DB 写操作在异步线程执行
// * - 使用线程安全集合保存已完成任务（finishedQueue）
// * - 停止时 flush 脏数据并取消定时任务
// *
// * 说明：依赖外部 TaskCache 与 DatabaseDAO 提供的方法（与原版一致）
// */
//public class DataSyncTask {
//    private final LoggerTools log;
//    private final UniversalScheduler scheduler;
//    private final TaskCache taskCache;
//    private final DatabaseDAO databaseDAO;
//
//    // 周期性任务句柄
//    private UniversalTask mainTask;
//
//    // 线程安全队列，用于存放已确认完成的任务，以便批量同步（避免多次写）
//    private final ConcurrentLinkedQueue<TaskProgress> finishedQueue = new ConcurrentLinkedQueue<>();
//
//    // 标记第一次保存（用于区分 startTask）
//    private final AtomicBoolean firstSaveDone = new AtomicBoolean(false);
//
//    // 是否正在运行
//    private final AtomicBoolean running = new AtomicBoolean(false);
//
//    public DataSyncTask(LoggerTools log, UniversalScheduler scheduler, TaskCache taskCache, DatabaseDAO databaseDAO) {
//        this.log = log;
//        this.scheduler = scheduler;
//        this.taskCache = taskCache;
//        this.databaseDAO = databaseDAO;
//
//
//    }
//
//    /**
//     * 启动主同步任务
//     *
//     * @param initialDelayTicks 启动延迟（刻，1s=20tick）
//     * @param periodTicks       周期（刻）
//     */
//    public void startSync(long initialDelayTicks, long periodTicks) {
//        if (running.getAndSet(true)) {
//            log.warn("DataSyncTask already running");
//            return;
//        }
//
//        // schedule a repeating async task that performs snapshots and DB writes
//        mainTask = scheduler.runTimerAsync(() -> {
//            try {
//                // 1) 快照脏数据并保存到 DB（异步写）
//                syncCacheToDatabaseSnapshot();
//
//                // 2) 筛选并同步已经完成的任务（来自 taskCache 的 maybeFinishedPlayers）
//                syncFinishedTasks();
//
//                // 3) 清理离线玩家缓存（如果 TaskCache 提供 offlinePlayers 列表，传入使用；否则默认逻辑在 cache 层处理）
//                cleanupOfflinePlayers();
//
//            } catch (Exception e) {
//                log.error("DataSyncTask 主任务发生异常", e);
//            }
//        }, initialDelayTicks, periodTicks);
//
//        log.info("DataSyncTask started, period (ticks): " + periodTicks);
//    }
//
//    /**
//     * 停止并 flush 脏数据
//     */
//    public void stopSync() {
//        if (!running.getAndSet(false)) {
//            log.debug("DataSyncTask not running");
//            return;
//        }
//
//        // 取消定时任务
//        if (mainTask != null) {
//            try {
//                mainTask.cancel();
//            } catch (Exception e) {
//                log.error("取消 mainTask 时出错", e);
//            }
//            mainTask = null;
//        }
//
//        // flush 当前脏数据（同步执行，确保进程退出前数据持久化）
//        try {
//            flushAllDirty();
//        } catch (Exception e) {
//            log.error("停止时 flush 脏数据失败", e);
//        }
//
//        log.info("DataSyncTask stopped and flushed.");
//    }
//
//    // === 内部逻辑实现 ===
//
//    /**
//     * 快照脏数据并异步写入数据库
//     *
//     * 1) 从 taskCache 获取 dirty UUIDs（快照）
//     * 2) 对每个 UUID，从 cache 中深拷贝对应的 List<TaskProgress>
//     * 3) 在 scheduler.runAsync 中批量写入 DB，写完后从原 dirty 集合中移除已经写入的 UUID
//     */
//    private void syncCacheToDatabaseSnapshot() {
//        Set<UUID> dirtySnapshot = new HashSet<>(taskCache.dirtyUUIDs()); // 快照
//        if (dirtySnapshot.isEmpty()) {
//            log.debug("没有脏数据需要同步");
//            return;
//        }
//
//        // 深拷贝 cache: map->(uuid -> new ArrayList<>(list))
//        Map<UUID, List<TaskProgress>> cacheSnapshot = new HashMap<>();
//        Map<UUID, List<TaskProgress>> originalCache = taskCache.progressByUUID();
//        for (UUID uuid : dirtySnapshot) {
//            List<TaskProgress> list = originalCache.get(uuid);
//            if (list != null && !list.isEmpty()) {
//                cacheSnapshot.put(uuid, new ArrayList<>(list));
//            }
//        }
//
//        // 异步批量写数据库
//        scheduler.runAsync(() -> {
//            List<TaskProgress> batchToSave = new ArrayList<>();
//            for (Map.Entry<UUID, List<TaskProgress>> e : cacheSnapshot.entrySet()) {
//                batchToSave.addAll(e.getValue());
//            }
//            if (batchToSave.isEmpty()) {
//                return;
//            }
//
//            // 区分第一次保存（例如玩家首次任务入库）与后续更新
//            if (!firstSaveDone.get()) {
//                try {
//                    databaseDAO.startTask(batchToSave);
//                    databaseDAO.setProgress(batchToSave);
//                    firstSaveDone.set(true);
//                    log.info("首次批量保存玩家任务到数据库成功，任务数量：" + batchToSave.size());
//                } catch (SQLException ex) {
//                    log.error("首次批量保存玩家任务到数据库失败", ex);
//                }
//            } else {
//                try {
//                    databaseDAO.updateTasks(batchToSave);
//                    databaseDAO.updateProgress(batchToSave);
//                    log.debug("批量更新玩家任务到数据库成功，任务数量：" + batchToSave.size());
//                } catch (SQLException ex) {
//                    log.error("批量更新玩家任务到数据库失败", ex);
//                }
//            }
//
//            // 写完后从原 dirty 集合中移除已保存的 UUID（线程安全：直接调用 taskCache 的 dirty 集合）
//            // 注意：这里基于 taskCache.dirtyUUIDs() 返回的是一个线程安全集合或合理设计；若不是，应提供 taskCache.clearDirty(uuid) 方法
//            for (UUID uuid : cacheSnapshot.keySet()) {
//                taskCache.dirtyUUIDs().remove(uuid);
//            }
//        });
//    }
//
//    /**
//     * 筛选 maybeFinishedPlayers 并批量更新到 DB（只更新进度）
//     * 将筛选出的已完成任务加入 finishedQueue（线程安全队列）
//     */
//    private void syncFinishedTasks() {
//        // 从 cache 拷贝 maybeFinishedPlayers（避免并发）
//        Set<TaskProgress> maybeFinishedSnapshot = new HashSet<>(taskCache.maybeUUIDs());
//        if (maybeFinishedSnapshot.isEmpty()) {
//            return;
//        }
//
//        for (TaskProgress tp : maybeFinishedSnapshot) {
//            boolean allFinished = true;
//            for (TaskTarget t : tp.getTask().getTargets()) {
//                if (!t.isFinished()) {
//                    allFinished = false;
//                    break;
//                }
//            }
//            if (allFinished) {
//                finishedQueue.add(tp);
//            }
//        }
//
//        // 清空 maybeFinishedPlayers（已将完成项转移到 finishedQueue）
//        taskCache.maybeUUIDs().clear();
//
//        // 如果有完成队列，则批量更新数据库（一次性写入 finishedQueue 的当前内容）
//        if (!finishedQueue.isEmpty()) {
//            List<TaskProgress> batch = new ArrayList<>();
//            TaskProgress polled;
//            while ((polled = finishedQueue.poll()) != null) {
//                batch.add(polled);
//            }
//
//            if (!batch.isEmpty()) {
//                scheduler.runAsync(() -> {
//                    try {
//                        databaseDAO.updateProgress(batch);
//                        for (TaskProgress done : batch) {
//                            log.debug(String.format("玩家 %s 任务 %s 已同步为完成", done.getUUID(), done.getTask().getId()));
//                            // 可选：从缓存移除该任务（如果业务要求）
//                            // taskCache.removeProgress(done.getUUID(), done.getTask().getId());
//                        }
//                    } catch (SQLException e) {
//                        log.error("同步已完成任务进度到数据库失败", e);
//                    }
//                });
//            }
//        }
//    }
//
//    /**
//     * 清理离线玩家缓存（如果 TaskCache 提供 offline info 则由外部传入或由 Cache 提供）
//     * 这里做一个保守实现：检查 taskCache 中所有玩家，如果某玩家已经被标记 dirty 且被判定为离线，则移除其 cache 并保存
//     *
//     * 注意：如何判断离线玩家由 taskCache 提供。如果没有，请把 offlinePlayers 列表提供给本类或改为由外部调用 clearCache
//     */
//    private void cleanupOfflinePlayers() {
//        List<UUID> offlinePlayers = taskCache.getOfflinePlayers(); // 假设 TaskCache 提供该方法；若没有需调整
//        if (offlinePlayers == null || offlinePlayers.isEmpty()) return;
//
//        Map<UUID, List<TaskProgress>> cache = taskCache.progressByUUID();
//        Set<UUID> dirty = taskCache.dirtyUUIDs();
//
//        // 使用迭代器安全删除
//        Iterator<Map.Entry<UUID, List<TaskProgress>>> iterator = cache.entrySet().iterator();
//        List<UUID> processed = new ArrayList<>();
//
//        while (iterator.hasNext()) {
//            Map.Entry<UUID, List<TaskProgress>> entry = iterator.next();
//            UUID uuid = entry.getKey();
//            if (offlinePlayers.contains(uuid) && dirty.contains(uuid)) {
//                List<TaskProgress> copyList = new ArrayList<>(entry.getValue());
//                try {
//                    databaseDAO.updateTasks(copyList);
//                    databaseDAO.updateProgress(copyList);
//                } catch (SQLException e) {
//                    log.error("离线玩家缓存同步失败，uuid=" + uuid, e);
//                    continue; // 出错则跳过删除，保留以便下次重试
//                }
//
//                iterator.remove(); // 从缓存移除
//                dirty.remove(uuid);
//                processed.add(uuid);
//            }
//        }
//
//        offlinePlayers.removeAll(processed);
//        if (!processed.isEmpty()) {
//            log.debug(String.format("已清理 %d 个玩家的缓存, 剩余 %d 个玩家", processed.size(), offlinePlayers.size()));
//        }
//    }
//
//    /**
//     * 停止时强制 flush 脏数据（同步阻塞调用）
//     */
//    private void flushAllDirty() {
//        // 1) flush dirty players
//        Set<UUID> dirty = new HashSet<>(taskCache.dirtyUUIDs());
//        if (!dirty.isEmpty()) {
//            List<TaskProgress> allDirty = new ArrayList<>();
//            Map<UUID, List<TaskProgress>> cache = taskCache.progressByUUID();
//            for (UUID uuid : dirty) {
//                List<TaskProgress> list = cache.get(uuid);
//                if (list != null && !list.isEmpty()) {
//                    allDirty.addAll(new ArrayList<>(list));
//                }
//            }
//
//            if (!allDirty.isEmpty()) {
//                // 直接在当前线程同步写入，确保进程停止前数据已写
//                try {
//                    if (!firstSaveDone.get()) {
//                        databaseDAO.startTask(allDirty);
//                        databaseDAO.setProgress(allDirty);
//                        firstSaveDone.set(true);
//                        log.info("停止时首次批量保存成功，数量：" + allDirty.size());
//                    } else {
//                        databaseDAO.updateTasks(allDirty);
//                        databaseDAO.updateProgress(allDirty);
//                        log.info("停止时批量更新成功，数量：" + allDirty.size());
//                    }
//                } catch (SQLException e) {
//                    log.error("停止时批量保存脏数据失败", e);
//                }
//            }
//            // 清理 dirty 集合（尽可能）
//            taskCache.dirtyUUIDs().removeAll(dirty);
//        }
//
//        // 2) flush finishedQueue（如果还有）
//        List<TaskProgress> remainingFinished = new ArrayList<>();
//        TaskProgress tp;
//        while ((tp = finishedQueue.poll()) != null) {
//            remainingFinished.add(tp);
//        }
//        if (!remainingFinished.isEmpty()) {
//            try {
//                databaseDAO.updateProgress(remainingFinished);
//                log.info("停止时同步已完成任务数量：" + remainingFinished.size());
//            } catch (SQLException e) {
//                log.error("停止时同步已完成任务失败", e);
//            }
//        }
//    }
//}
