package com.playerPlugin.playerTaskX.dataManager.cache;

import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.playerPlugin.playerTaskX.utils.Help.log;
import static com.playerPlugin.playerTaskX.utils.Help.sm;


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
    private static final int MAX_CACHE_SIZE = 100;
    // LRU缓存 - 使用LinkedHashMap实现LRU策略
    private final Map<UUID, List<PlayerTask>> cache;

    // 脏数据标记 - 记录需要保存到数据库的数据
    private final Set<UUID> dirtyEntries = ConcurrentHashMap.newKeySet();
    private final Set<PlayerTask> maybeFinishedEntries = ConcurrentHashMap.newKeySet();

    public PlayerTaskCache() {
        // 初始化LRU缓存
        this.cache = Collections.synchronizedMap(
                new LinkedHashMap<UUID, List<PlayerTask>>(
                        MAX_CACHE_SIZE, // 初始容量 100个玩家 TODO 配置文件自定义
                        0.75f,
                        true
                ) {
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
    }

    /**
     * 获取缓存
     *
     * @return {@link Map }<{@link UUID }, {@link List }<{@link PlayerTask }>>
     */
    public Map<UUID, List<PlayerTask>> getCache() {
        return cache;
    }

    public Set<UUID> getDirtyEntries() {
        return dirtyEntries;
    }

    /**
     * 获取所有可能完成任务的玩家
     *
     * @return {@link Set }<{@link PlayerTask }>
     */
    public Set<PlayerTask> getMaybeFinishedPlayers() {
        return maybeFinishedEntries;
    }

    /**
     * 添加可能完成任务的玩家
     *
     * @param playerTask 玩家任务
     */
    public void addMaybeFinishedPlayer(PlayerTask playerTask) {
        maybeFinishedEntries.add(playerTask);
    }

    /**
     * 将玩家任务添加到缓存
     *
     * @param playerTaskList 玩家任务列表
     * @param immediateSave  立即保存
     */
    public void addPlayerTaskToCache(List<PlayerTask> playerTaskList, boolean immediateSave) {
        for (PlayerTask playerTask : playerTaskList) {
            UUID uuid = playerTask.getUUID();

            List<PlayerTask> taskList = cache.computeIfAbsent(uuid, k -> new ArrayList<>());

            // TODO
            // 必须重写 PlayerTask 的 equals() 和 hashCode()
            // taskList.contains(playerTask) 依赖 PlayerTask 的 equals() 方法判断「两个任务是否相同」。如果没重写，会使用 Object 类的默认实现（仅判断对象引用是否相同），导致去重失效！
            if (!taskList.contains(playerTask)) {
                taskList.add(playerTask);
            }

            if (immediateSave) {
                startPlayerTask(List.of(playerTask));
            } else {
                dirtyEntries.add(uuid);
            }
        }
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
                log.info("当前执行：更新到缓存，任务ID：" + playerTask.getTask().getId() + "，状态：" + playerTask.getStatus() + "，立即保存：" + immediateSave);
            } else {
                log.info("当前执行：添加到缓存，任务ID：" + playerTask.getTask().getId() + "，状态：" + playerTask.getStatus() + "，立即保存：" + immediateSave);
            }
        });
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
            sm.getDatabaseDAO().updateTasks(playerTasks);
            sm.getDatabaseDAO().updateProgress(playerTasks);
            log.debug("批量保存玩家任务到数据库成功，任务数量：" + playerTasks.size());
        } catch (SQLException e) {
            log.error("批量保存玩家任务到数据库失败", e);
        }
    }

    private void startPlayerTask(List<PlayerTask> playerTasks) {
        if (playerTasks == null || playerTasks.isEmpty()) {
            return;
        }

        try {
            sm.getDatabaseDAO().startTask(playerTasks);
            sm.getDatabaseDAO().setProgress(playerTasks);
            log.debug("批量开始玩家任务成功，任务数量：" + playerTasks.size());
        } catch (SQLException e) {
            log.error("批量开始玩家任务失败", e);
        }
    }
}