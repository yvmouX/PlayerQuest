package com.playerPlugin.playerTaskX.dataManager.dao;

import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.dataManager.cache.PlayerTaskCache;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

import static com.playerPlugin.playerTaskX.utils.Help.log;

public class CacheDAO {
    private final PlayerTaskCache playerTaskCache;

    public CacheDAO(PlayerTaskCache playerTaskCache) {
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
     * @param uuid uuid
     */
    public void addMaybeFinishedPlayer(UUID uuid) {
        if (playerTaskCache.getCache().get(uuid) == null) {
            log.warn("尝试将可能完成任务的玩家添加到 maybeFinishedPlayers，但无法从缓存中获取到该玩家的任务列表：" + uuid);
            return;
        }
        playerTaskCache.addMaybeFinishedPlayer(uuid);
    }
}
