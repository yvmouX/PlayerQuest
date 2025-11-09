package com.playerPlugin.playerTaskX.dataManager.cache;

import cn.yvmou.ylib.api.scheduler.UniversalTask;
import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.playerPlugin.playerTaskX.utils.Help.*;


/**
 * 玩家任务缓存管理器
 * 结合LRU缓存策略和写入缓冲区，提高性能和可靠性
 * <p>
 * 1.只保存在线玩家的任务数据
 * 2.定期为离线玩家的任务数据保存到数据库
 * </p>
 *
 * @author yvmoux
 * &#064;date  2025/11/03@date 2025/11/03
 */
public class PlayerTaskCache {
    // 缓存配置
    private static final int MAX_CACHE_SIZE = 100;
    private static final long SAVE_INTERVAL_SECONDS = 30 * 20; // 30秒保存一次
    private static final long CLEANUP_INTERVAL_SECONDS = 300; // 5分钟清理一次过期缓存
    private static volatile PlayerTaskCache instance;
    // LRU缓存 - 使用LinkedHashMap实现LRU策略
    private final Map<UUID, List<PlayerTask>> cache;

    // 脏数据标记 - 记录需要保存到数据库的数据
    private final Set<UUID> dirtyEntries = ConcurrentHashMap.newKeySet();
    private final Set<UUID> finishedEntries = ConcurrentHashMap.newKeySet(); // TODO 不代表任务完成，只要玩家单个目标完成，玩家uuid就会被添加到这个集合，后面会检查所有目标是否完成,如果所有目标都完成，会保存到数据库
    private final Set<UUID> maybeFinishedEntries = ConcurrentHashMap.newKeySet();

    private final List<UniversalTask> universalTask;

    /**
     * 获取缓存
     *
     * @return {@link Map }<{@link UUID }, {@link List }<{@link PlayerTask }>>
     */
    public Map<UUID, List<PlayerTask>> getCache() {
        return cache;
    }

    public void addCache(UUID uuid, PlayerTask task) {
        List<PlayerTask> playerTaskList = cache.computeIfAbsent(uuid, k -> new ArrayList<>());
        playerTaskList.add(task);

        dirtyEntries.add(uuid);
    }

    /**
     * 添加可能完成任务的玩家
     *
     * @param uuid uuid
     */
    public void addMaybeFinishedPlayer(UUID uuid) {
        maybeFinishedEntries.add(uuid);
    }


    public PlayerTaskCache() {
        this.universalTask = new ArrayList<>();
        // 初始化LRU缓存
        this.cache = Collections.synchronizedMap(new LinkedHashMap<UUID, List<PlayerTask>>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, List<PlayerTask>> eldest) {
                // 当缓存超过最大容量时，移除最久未使用的条目
                // 但如果是脏数据，先保存到数据库
                if (size() > MAX_CACHE_SIZE) {
                    if (dirtyEntries.contains(eldest.getKey())) {
                        savePlayerTasks(eldest.getValue());
                        dirtyEntries.remove(eldest.getKey());
                    }
                    return true;
                }
                return false;
            }
        });
        startTimerTasks();
    }

    private void startTimerTasks() {
        UniversalTask universalTask0 = scheduler.runTimer(() -> {
            List<UUID> savaUUID = new ArrayList<>();
            // 检查是否有任务完成，如果所有目标都完成，则设置任务状态为完成
            for (UUID uuid : finishedEntries) {
                List<PlayerTask> tasks = cache.get(uuid);
                if (tasks != null) {
                    for (PlayerTask task : tasks) {
                        AtomicBoolean allFinished = new AtomicBoolean(false);
                        task.getTask().getTargets().forEach(target -> {
                            allFinished.set(target.isFinished());
                        });

                        if (allFinished.get()) {
                            task.setStatus(PTXTaskStatus.COMPLETED);
                            savaUUID.add(task.getUUID());
                        }
                    }
                    // 从脏数据集合中移除
                    finishedEntries.remove(uuid);
                    logger.trace("finishedEntries", finishedEntries);
                }
            }

            if (savaUUID.isEmpty()) {
                logger.warn("savaUUID 是空的");
                return;
            }
            Set<UUID> copy = new HashSet<>(savaUUID);

            // 保存玩家进度到数据库 异步
            scheduler.runAsync(() -> {
                for (UUID uuid : copy) {
                    List<PlayerTask> tasks = cache.get(uuid);
                    if (tasks != null) {
                        sm.getPlayerTaskProgressDAO().updateProgress(tasks);
                    }
                }
            });

        }, 0, SAVE_INTERVAL_SECONDS);
        universalTask.add(universalTask0);

        // 启动定时保存任务 每30秒保存一次脏数据到数据库
        UniversalTask universalTask1 = scheduler.runTimer(() -> {
            if (dirtyEntries.isEmpty()) {
                return;
            }

            // 复制一份数据集合，避免并发修改
            Set<UUID> currentDirty = new HashSet<>(dirtyEntries);

            // 异步保存到数据库
            scheduler.runAsync(() -> {
                for (UUID uuid : currentDirty) {
                    List<PlayerTask> tasks = cache.get(uuid);
                    if (tasks != null) {
                        savePlayerTasks(tasks); // 保存任务到数据库
                        // 从脏数据集合中移除
                        dirtyEntries.remove(uuid);
                    }
                }
            });
        }, 0, SAVE_INTERVAL_SECONDS);
        universalTask.add(universalTask1);

        // 启动定时清理任务 清理离线玩家的缓存
        UniversalTask universalTask2 = scheduler.runTimer(() -> {
            Set<UUID> cachedPlayers = new HashSet<>(cache.keySet());

            for (UUID uuid : cachedPlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    // 玩家离线，检查是否有脏数据需要保存
                    if (dirtyEntries.contains(uuid)) {
                        savePlayerTasks(cache.get(uuid));
                        dirtyEntries.remove(uuid);
                    }
                    // 从缓存中移除
                    cache.remove(uuid);
                }
            }
        }, 0, CLEANUP_INTERVAL_SECONDS);
        universalTask.add(universalTask2);
    }






    /**
     * 更新玩家任务 / 添加新任务
     *
     * <p>
     * 如果缓存中不存在该玩家的任务列表，则添加新任务到缓存中
     * 如果缓存中存在该玩家的任务列表，则更新该任务
     * </p>
     *
     * @param playerTaskList 玩家任务列表
     * @param immediateSave  是否立即保存到数据库
     */
    public void updatePlayerTaskToCache(List<PlayerTask> playerTaskList, boolean immediateSave) {
        playerTaskList.forEach(playerTask -> {
            UUID uuid = playerTask.getUUID();

            List<PlayerTask> taskList = cache.computeIfAbsent(uuid, k -> new ArrayList<>());

            // 总结：taskList 不为空时更新任务
            // 如果在 cache 中找不到该 uuid 的任务列表
            // taskList.size() == 0 说明该玩家没有任务在 cache 中
            // 此时 updated 为 false，代表未对该玩家在 cache 中的任务进行更新
            boolean updated = false;
            for (int i = 0; i < taskList.size(); i++) {
                if (taskList.get(i).getTask().getId().equals(playerTask.getTask().getId())) {
                    taskList.set(i, playerTask);
                    updated = true;
                    break;
                }
            }

            // 若 updated 为 false，说明该玩家在 cache 中没有该任务
            // 则将该任务添加到 cache 中
            if (!updated) {
                taskList.add(playerTask);
            }

            // 如果 immediateSave 为 true，则立即保存到数据库
            // 否则，将该玩家添加到脏数据集合中，稍后保存到数据库
            if (immediateSave) {
                savePlayerTasks(Collections.singletonList(playerTask));
            } else {
                dirtyEntries.add(uuid);
            }

            if (updated) {
                logger.info("当前执行：更新到缓存，任务ID：" + playerTask.getTask().getId() + "，状态：" + playerTask.getStatus() + "，立即保存：" + immediateSave);
            } else {
                logger.info("当前执行：添加到缓存，任务ID：" + playerTask.getTask().getId() + "，状态：" + playerTask.getStatus() + "，立即保存：" + immediateSave);
            }
        });
    }

    /**
     * 关闭缓存管理器
     * 保存所有数据到数据库
     */
    public void shutdown() {
        // 收集所有脏数据并进行一次性批量保存
        List<PlayerTask> allDirtyTasks = new ArrayList<>();
        for (UUID uuid : dirtyEntries) {
            List<PlayerTask> tasks = cache.get(uuid);
            if (tasks != null) {
                allDirtyTasks.addAll(tasks);
            }
        }

        if (!allDirtyTasks.isEmpty()) {
            savePlayerTasks(allDirtyTasks);
        }
        dirtyEntries.clear();

        // 关闭调度器
        if (universalTask != null) {
            for (UniversalTask task : universalTask) {
                task.cancel();
            }
            universalTask.clear();
        }

    }

    /**
     * 保存玩家任务到数据库
     *
     * @param playerTasks 任务
     */
    private void savePlayerTasks(List<PlayerTask> playerTasks) {
        if (playerTasks == null || playerTasks.isEmpty()) {
            return;
        }

        try {
            sm.getPlayerTaskDAO().updateTasks(playerTasks);
            sm.getPlayerTaskProgressDAO().updateProgress(playerTasks);
            logger.debug("批量保存玩家任务到数据库成功，任务数量：" + playerTasks.size());
        } catch (SQLException e) {
            logger.error("批量保存玩家任务到数据库失败", e);
        }
    }

    private void startPlayerTask(List<PlayerTask> playerTasks) {
        if (playerTasks == null || playerTasks.isEmpty()) {
            return;
        }

        try {
            sm.getPlayerTaskDAO().startTask(playerTasks);
            sm.getPlayerTaskProgressDAO().setProgress(playerTasks);
            logger.debug("批量开始玩家任务成功，任务数量：" + playerTasks.size());
        } catch (SQLException e) {
            logger.error("批量开始玩家任务失败", e);
        }
    }


}