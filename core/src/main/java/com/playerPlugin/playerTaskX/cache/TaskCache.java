package com.playerPlugin.playerTaskX.cache;

import cn.yvmou.ylib.api.scheduler.UniversalScheduler;
import cn.yvmou.ylib.api.scheduler.UniversalTask;
import cn.yvmou.ylib.tools.LoggerTools;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.model.Task.TaskDefinition;
import com.playerPlugin.playerTaskX.model.Task.TaskProgress;
import com.playerPlugin.playerTaskX.model.Task.TaskTarget;
import com.playerPlugin.playerTaskX.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.storage.TaskRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在 `TaskService.reload()` 时用 `TaskRepository.loadAll()` 更新缓存；在 `UpdateProgressUseCase` 中优先从缓存读取任务定义。
 */

/**
 * Def缓存
 * 玩家进度缓存
 */
public class TaskCache {
    private static final int MAX_CAPACITY = 100; // 最大缓存容量 100个玩家 TODO 配置文件自定义

    // 周期性任务句柄
    private UniversalTask mainTask;
    // 线程安全队列，用于存放已确认完成的任务，以便批量同步（避免多次写）
    private final ConcurrentLinkedQueue<TaskProgress> finishedQueue = new ConcurrentLinkedQueue<>();
    // 标记第一次保存（用于区分 startTask）
    private final AtomicBoolean firstSaveDone = new AtomicBoolean(false);
    // 是否正在运行
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final LoggerTools log;
    private final UniversalScheduler scheduler;
    private final TaskRepository repo;
    private final TaskProgressRepository progressRepo;

    private final Map<UUID, List<TaskProgress>> progressByUUID; // 玩家UUID -> 任务进度列表
    private final Set<UUID> progressDirtyUUIDs = ConcurrentHashMap.newKeySet();
    private final Set<TaskProgress> progressMaybeUUIDs = ConcurrentHashMap.newKeySet();

    private final List<ConcurrentMap<String, TaskDefinition>> taskDefById = new CopyOnWriteArrayList<>(); // List<任务ID -> 任务定义>

    public Map<UUID, List<TaskProgress>> setProgressByUUID() {
        return progressByUUID;
    }
    public Set<UUID> setProgressDirtyUUIDs() {
        return progressDirtyUUIDs;
    }
    public Set<TaskProgress> setProgressMaybeUUIDs() {
        return progressMaybeUUIDs;
    }
    public List<ConcurrentMap<String, TaskDefinition>> setTaskDefById() {
        return taskDefById;
    }

    public TaskCache(LoggerTools log, UniversalScheduler scheduler, TaskRepository repo, TaskProgressRepository progressRepo) {
        this.log = log;
        this.scheduler = scheduler;
        this.repo = repo;
        this.progressRepo = progressRepo;
        progressByUUID = Collections.synchronizedMap(
                new LinkedHashMap<UUID, List<TaskProgress>>(
                        MAX_CAPACITY,
                        0.75f,
                        true
                ) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<UUID, List<TaskProgress>> eldest) {
                        // 当缓存超过最大容量时，移除最久未使用的条目
                        // 但如果是脏数据，先保存到数据库
                        if (size() > MAX_CAPACITY) {
                            if (progressDirtyUUIDs.contains(eldest.getKey())) {
                                eldest.getValue().forEach(progressRepo::save);
                                progressDirtyUUIDs.remove(eldest.getKey());
                            }
                            return true;
                        }
                        return false;
                    }
                });
    }


    // DAO 方法
    public void progressToCache(TaskProgress taskProgress) {
        UUID uuid = taskProgress.getUUID();

        List<TaskProgress> taskProgressList = progressByUUID.computeIfAbsent(uuid, k -> new ArrayList<>());

        // TODO
        // 必须重写 TaskProgress 的 equals() 和 hashCode()
        // taskList.contains(taskProgress) 依赖 TaskProgress 的 equals() 方法判断「两个任务是否相同」。如果没重写，会使用 Object 类的默认实现（仅判断对象引用是否相同），导致去重失效！
        if (!taskProgressList.contains(taskProgress)) {
            taskProgressList.add(taskProgress);
        }
    }

    public void progressToRepository(TaskProgress progress, boolean immediateSave) {
        if (immediateSave) {
            progressRepo.save(progress);
        } else {
            progressDirtyUUIDs.add(progress.getUUID());
        }
    }

    // ===================常用方法==========================
    /**
     * 获取指定玩家和任务状态的任务进度列表，从缓存中获取
     *
     * @param uuid   UUID
     * @param status 任务状态
     * @return @return {@link List }<{@link TaskProgress }>
     */
    @javax.annotation.Nullable
    @org.jetbrains.annotations.Nullable
    public List<TaskProgress> getProgressList(UUID uuid, PTXTaskStatus status) {
        return progressByUUID.get(uuid).stream()
                .filter(taskProgress -> taskProgress.getStatus() == status)
                .toList();
    }

    /**
     * 获取指定玩家的所有任务进度列表，从缓存中获取
     *
     * @param uuid UUID
     * @return {@link List }<{@link TaskProgress }>
     */
    @javax.annotation.Nullable
    @org.jetbrains.annotations.Nullable
    public List<TaskProgress> getProgressList(UUID uuid) {
        return progressByUUID.get(uuid);
    }

    /**
     * 按ID获取任务定义
     *
     * @param id 任务ID
     * @return {@link TaskDefinition }
     */
    @javax.annotation.Nullable
    @org.jetbrains.annotations.Nullable
    public TaskDefinition getTaskDef(String id) {
        if (id == null || id.isBlank()) return null;
        for (ConcurrentMap<String, TaskDefinition> map : taskDefById) {
            TaskDefinition taskDef = map.get(id);
            if (taskDef != null) return taskDef;
        }
        return null;
    }

    /**
     * 获取任务定义列表
     *
     * @return {@link List }<{@link TaskDefinition }>
     */
    @javax.annotation.Nullable
    @org.jetbrains.annotations.Nullable
    public List<TaskDefinition> getTaskDefList() {
        List<TaskDefinition> result = new ArrayList<>();
        for (ConcurrentMap<String, TaskDefinition> map : taskDefById) {
            result.addAll(map.values());
        }
        return result;
    }

    // ===================私有方法：数据同步==================
    private void startSync(long initialDelayTicks, long periodTicks) {
        if (running.getAndSet(true)) {
            log.warn("DataSyncTask already running");
            return;
        }

        // schedule a repeating async task that performs snapshots and DB writes
        mainTask = scheduler.runTimerAsync(() -> {
            try {
                // 1) 快照脏数据并保存到 DB（异步写）
                syncCacheToDatabaseSnapshot();

                // 2) 筛选并同步已经完成的任务（来自 taskCache 的 maybeFinishedPlayers）
                syncFinishedTasks();

            } catch (Exception e) {
                log.error("DataSyncTask 主任务发生异常", e);
            }
        }, initialDelayTicks, periodTicks);

        log.info("DataSyncTask started, period (ticks): " + periodTicks);
    }

    private void stopSync() {
        if (!running.getAndSet(false)) {
            log.debug("DataSyncTask not running");
            return;
        }

        // 取消定时任务
        if (mainTask != null) {
            try {
                mainTask.cancel();
            } catch (Exception e) {
                log.error("取消 mainTask 时出错", e);
            }
            mainTask = null;
        }

        // flush 当前脏数据（同步执行，确保进程退出前数据持久化）
        try {
            flushAllDirty();
        } catch (Exception e) {
            log.error("停止时 flush 脏数据失败", e);
        }

        log.info("DataSyncTask stopped and flushed.");
    }

    // === 内部逻辑实现 ===
    private void syncCacheToDatabaseSnapshot() {
        Set<UUID> dirtySnapshot = new HashSet<>(progressDirtyUUIDs); // 快照
        if (dirtySnapshot.isEmpty()) {
            log.debug("没有脏数据需要同步");
            return;
        }

        // 深拷贝 cache: map->(uuid -> new ArrayList<>(list))
        Map<UUID, List<TaskProgress>> cacheSnapshot = new HashMap<>();
        for (UUID uuid : dirtySnapshot) {
            List<TaskProgress> list = progressByUUID.get(uuid);
            if (list != null && !list.isEmpty()) {
                cacheSnapshot.put(uuid, new ArrayList<>(list));
            }
        }

        // 异步批量写入仓库
        scheduler.runAsync(() -> {
            List<TaskProgress> batchToSave = new ArrayList<>();
            for (Map.Entry<UUID, List<TaskProgress>> e : cacheSnapshot.entrySet()) {
                batchToSave.addAll(e.getValue());
            }
            if (batchToSave.isEmpty()) {
                return;
            }
            try {
                batchToSave.forEach(progressRepo::save);
                log.debug("批量保存玩家任务到仓库成功，任务数量: " + batchToSave.size());
            } catch (Exception e) {
                log.error("批量保存玩家任务到仓库失败", e);
            }

            for (UUID uuid : cacheSnapshot.keySet()) {
                progressDirtyUUIDs.remove(uuid);
            }
        });
    }

    /**
     * 筛选 progressMaybeUUIDs 并批量更新到 DB（只更新进度）
     * 将筛选出的已完成任务加入 finishedQueue（线程安全队列）
     */
    private void syncFinishedTasks() {
        // 从 cache 拷贝 maybeFinishedPlayers（避免并发）
        Set<TaskProgress> maybeFinishedSnapshot = new HashSet<>(progressMaybeUUIDs);
        if (maybeFinishedSnapshot.isEmpty()) {
            return;
        }

        for (TaskProgress tp : maybeFinishedSnapshot) {
            boolean allFinished = true;
            for (TaskTarget t : tp.getTask().getTargets()) {
                if (!t.isFinished()) {
                    allFinished = false;
                    break;
                }
            }
            if (allFinished) {
                finishedQueue.add(tp);
            }
        }

        // 清空 maybeFinishedPlayers（已将完成项转移到 finishedQueue）
        progressMaybeUUIDs.clear();

        // 如果有完成队列，则批量更新数据库（一次性写入 finishedQueue 的当前内容）
        if (!finishedQueue.isEmpty()) {
            List<TaskProgress> batch = new ArrayList<>();
            TaskProgress polled;
            while ((polled = finishedQueue.poll()) != null) {
                batch.add(polled);
            }

            if (!batch.isEmpty()) {
                scheduler.runAsync(() -> {
                    try {
                        for (TaskProgress done : batch) {
                            progressRepo.save(done);
                            log.debug(String.format("玩家 %s 任务 %s 已同步为完成", done.getUUID(), done.getTask().getId()));
                        }
                    } catch (Exception e) {
                        log.error("同步已完成任务进度到数据库失败", e);
                    }
                });
            }
        }
    }


    /**
     * 停止时强制 flush 脏数据（同步阻塞调用）
     */
    private void flushAllDirty() {
        // 1) flush dirty players
        Set<UUID> dirty = new HashSet<>(progressDirtyUUIDs);
        if (!dirty.isEmpty()) {
            List<TaskProgress> allDirty = new ArrayList<>();
            for (UUID uuid : dirty) {
                List<TaskProgress> list = progressByUUID.get(uuid);
                if (list != null && !list.isEmpty()) {
                    allDirty.addAll(new ArrayList<>(list));
                }
            }

            if (!allDirty.isEmpty()) {
                try {
                    for (TaskProgress d : allDirty) {
                        progressRepo.save(d);
                    }
                    log.info("停止时首次批量保存成功，数量：" + allDirty.size());
                } catch (Exception e) {
                    log.error("停止时批量保存脏数据失败", e);
                }
            }
            // 清理 dirty 集合（尽可能）
            progressDirtyUUIDs.removeAll(dirty);
        }

        // 2) flush finishedQueue（如果还有）
        List<TaskProgress> remainingFinished = new ArrayList<>();
        TaskProgress tp;
        while ((tp = finishedQueue.poll()) != null) {
            remainingFinished.add(tp);
        }
        if (!remainingFinished.isEmpty()) {
            try {
                for (TaskProgress d : remainingFinished) {
                    progressRepo.save(d);
                }
                log.info("停止时同步已完成任务数量：" + remainingFinished.size());
            } catch (Exception e) {
                log.error("停止时同步已完成任务失败", e);
            }
        }
    }




//
//    /**
//     * 更新玩家任务 / 添加新任务
//     *
//     * <p>
//     * 如果缓存中不存在该玩家的任务列表，则添加新任务到缓存中
//     * 如果缓存中存在该玩家的任务列表，则更新该任务
//     * </p>
//     *
//     * @param taskProgressList 玩家任务列表
//     * @param immediateSave  是否立即保存到数据库
//     */
//    public void updatePlayerTaskToCache(List<TaskProgress> taskProgressList, boolean immediateSave) {
//        taskProgressList.forEach(playerTask -> {
//            UUID uuid = playerTask.getUUID();
//
//            List<TaskProgress> taskList = cache.getCache().computeIfAbsent(uuid, k -> new ArrayList<>());
//
//            // 总结：taskList 不为空时更新任务
//            // 如果在 cache 中找不到该 uuid 的任务列表
//            // taskList.size() == 0 说明该玩家没有任务在 cache 中
//            // 此时 updated 为 false，代表未对该玩家在 cache 中的任务进行更新
//            boolean updated = false;
//            for (int i = 0; i < taskList.size(); i++) {
//                if (taskList.get(i).getTask().getId().equals(playerTask.getTask().getId())) {
//                    taskList.set(i, playerTask);
//                    updated = true;
//                    break;
//                }
//            }
//
//            // 若 updated 为 false，说明该玩家在 cache 中没有该任务
//            // 则将该任务添加到 cache 中
//            if (!updated) {
//                taskList.add(playerTask);
//            }
//
//            // 如果 immediateSave 为 true，则立即保存到数据库
//            // 否则，将该玩家添加到脏数据集合中，稍后保存到数据库
//            if (immediateSave) {
//                saveCacheToDatabase(Collections.singletonList(playerTask), false);
//            } else {
//                cache.getDirtyEntries().add(uuid);
//            }
//
//            if (updated) {
//                log.info("当前执行：更新到缓存，任务ID：" + playerTask.getTask().getId() + "，状态：" + playerTask.getStatus() + "，立即保存：" + immediateSave);
//            } else {
//                log.info("当前执行：添加到缓存，任务ID：" + playerTask.getTask().getId() + "，状态：" + playerTask.getStatus() + "，立即保存：" + immediateSave);
//            }
//        });
//    }



//
//    /**
//     * 增加玩家任务进度
//     *
//     * @param taskProgress 玩家任务
//     * @param material     材料
//     * @param progress     进展数量
//     */
//    public void increasePlayerTaskProgress(TaskProgress taskProgress, Material material, int progress) {
//        taskProgress.getTask().getTargets().forEach(target -> {
//            switch (target.getAction()) {
//                case DROP -> {}
//                case TAKE -> {}
//                case KILL -> {}
//                case TAME -> {}
//                case BREAK -> {}
//                case CRAFT -> {}
//                case BREED -> {}
//                case PLACE -> {
//                    Requirement r = target.getRequirement();
//                    if (r.getMaterial() == material) {
//                        if (target.incrementCurrent(progress)) {
//                            sm.getCacheDAO().addMaybeFinishedPlayer(taskProgress);
//                        }
//                    }
//                }
//                case CONSUME -> {}
//                case ENCHANT -> {}
//                case FISHING -> {}
//                case SCISSOR -> {}
//                case TRIGGER -> {}
//                case NONE -> {}
//            }
//        });
//    }
//
//
//    /**
//     * 开始任务
//     *
//     * @param player 选手
//     * @param taskId 任务 ID
//     */
//    public void startTask(Player player, String taskId) throws SQLException {
//        UUID uuid = player.getUniqueId();
//        TaskDefinition taskDefinition = tm.getTask(taskId);
//
//        if (taskDefinition == null) {
//            player.sendMessage("§c任务 %s 不存在", taskId);
//            return;
//        }
//
//        // 直接从 数据库 获取玩家进行中的任务列表
//        // 检测是否已经有该任务。
//        AtomicBoolean canStart = new AtomicBoolean(true);
//        for (String id : sm.getTaskIdListFromDatabase(uuid, PTXTaskStatus.IN_PROGRESS)) {
//            if (id.equals(taskId)) {
//                player.sendMessage("§c你已经接受或者完成过任务: §e" + taskDefinition.getName() + "！");
//                canStart.set(false);
//            }
//        }
//
//        // 向缓存添加任务
//        if (canStart.get()) {
//            sm.getCacheDAO().addPlayerTaskToCache(List.of(new TaskProgress(uuid, taskDefinition)), true);
//        }
//
//        // 执行任务开始触发器
//        TaskTriggerExecutor.execute(player, taskDefinition.getTrigger().getOnTaskStart(), taskDefinition);
//
//        player.sendMessage("§a你已开始任务: §e" + taskDefinition.getName());
//    }
}