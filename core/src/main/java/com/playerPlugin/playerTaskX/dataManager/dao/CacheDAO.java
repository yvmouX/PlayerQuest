package com.playerPlugin.playerTaskX.dataManager.dao;

import com.playerPlugin.playerTaskX.domain.PlayerTask.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.domain.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import com.playerPlugin.playerTaskX.dataManager.cache.PlayerTaskCache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.playerPlugin.playerTaskX.utils.Help.log;

public class CacheDAO {
    private final StorgeManager sm;
    private final PlayerTaskCache playerTaskCache;

    public CacheDAO(StorgeManager sm, PlayerTaskCache playerTaskCache) {
        this.sm = sm;
        this.playerTaskCache = playerTaskCache;
    }

    /**
     * 获取玩家任务缓存
     *
     * @return {@link PlayerTaskCache }
     */
    public PlayerTaskCache getPlayerTaskCache() {
        return playerTaskCache;
    }

    /**
     * 获取指定玩家和任务状态的任务列表，从缓存中获取
     *
     * @param uuid   玩家UUID
     * @param status 任务状态
     * @return {@link List }<{@link PlayerTask }>
     */
    @Nullable
    @org.jetbrains.annotations.Nullable
    public List<PlayerTask> getPlayerTaskListFormCache(UUID uuid, PTXTaskStatus status) {
        return playerTaskCache.getCache().get(uuid).stream()
                .filter(playerTask -> playerTask.getStatus() == status)
                .toList();
    }

    /**
     * 获取指定玩家的所有任务列表，从缓存中获取
     *
     * @param uuid uuid
     * @return {@link List }<{@link PlayerTask }>
     */
    @Nullable
    @org.jetbrains.annotations.Nullable
    public List<PlayerTask> getPlayerTaskListFormCache(UUID uuid) {
        return playerTaskCache.getCache().get(uuid).stream()
                .toList();
    }

    /**
     * 添加可能完成任务的玩家到缓存
     *
     * @param playerTask 玩家任务
     */
    public void addMaybeFinishedPlayer(PlayerTask playerTask) {
        if (playerTaskCache.getCache().get(playerTask.getUUID()) == null) {
            log.warn("尝试将可能完成任务的玩家添加到 maybeFinishedPlayers，但无法从缓存中获取到该玩家的任务列表：" + playerTask.getUUID());
            return;
        }
        playerTaskCache.addMaybeFinishedPlayer(playerTask);
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

            List<PlayerTask> taskList = playerTaskCache.getCache().computeIfAbsent(uuid, k -> new ArrayList<>());

            // TODO
            // 必须重写 PlayerTask 的 equals() 和 hashCode()
            // taskList.contains(playerTask) 依赖 PlayerTask 的 equals() 方法判断「两个任务是否相同」。如果没重写，会使用 Object 类的默认实现（仅判断对象引用是否相同），导致去重失效！
            if (!taskList.contains(playerTask)) {
                taskList.add(playerTask);
            }

            if (immediateSave) {
                saveCacheToDatabase(List.of(playerTask), true);
            } else {
                playerTaskCache.getDirtyEntries().add(uuid);
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

            List<PlayerTask> taskList = playerTaskCache.getCache().computeIfAbsent(uuid, k -> new ArrayList<>());

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
                saveCacheToDatabase(Collections.singletonList(playerTask), false);
            } else {
                playerTaskCache.getDirtyEntries().add(uuid);
            }

            if (updated) {
                log.info("当前执行：更新到缓存，任务ID：" + playerTask.getTask().getId() + "，状态：" + playerTask.getStatus() + "，立即保存：" + immediateSave);
            } else {
                log.info("当前执行：添加到缓存，任务ID：" + playerTask.getTask().getId() + "，状态：" + playerTask.getStatus() + "，立即保存：" + immediateSave);
            }
        });
    }

    private void saveCacheToDatabase(@Nonnull List<PlayerTask> playerTaskList, boolean firstSave) {
        if (playerTaskList.isEmpty()) return;

        if (firstSave) {
            try {
                sm.getDatabaseDAO().startTask(playerTaskList);
                sm.getDatabaseDAO().setProgress(playerTaskList);
                log.info("批量保存玩家任务到数据库成功，任务数量：" + playerTaskList.size());
            } catch (SQLException e) {
                log.error("批量保存玩家任务到数据库失败", e);
            }
        } else {
            try {
                sm.getDatabaseDAO().updateTasks(playerTaskList);
                sm.getDatabaseDAO().updateProgress(playerTaskList);
                log.debug("批量保存玩家任务到数据库成功，任务数量：" + playerTaskList.size());
            } catch (SQLException e) {
                log.error("批量保存玩家任务到数据库失败", e);
            }
        }
    }
}
