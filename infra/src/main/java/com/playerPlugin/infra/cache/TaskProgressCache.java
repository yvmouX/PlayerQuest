package com.playerPlugin.infra.cache;

import com.playerPlugin.core.domain.Task.TaskProgress;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


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
public class TaskProgressCache {

    // LRU缓存 - 使用LinkedHashMap实现LRU策略
    private Map<UUID, List<TaskProgress>> cache;

    // 脏数据标记 - 记录需要保存到数据库的数据
    private final Set<UUID> dirtyEntries = ConcurrentHashMap.newKeySet();
    private final Set<TaskProgress> maybeFinishedEntries = ConcurrentHashMap.newKeySet();

    public TaskProgressCache() {
    }

    public void init(Map<UUID, List<TaskProgress>> playerTaskCache) {
        this.cache = playerTaskCache;
    }

    /**
     * 获取缓存
     *
     * @return {@link Map }<{@link UUID }, {@link List }<{@link TaskProgress }>>
     */
    public Map<UUID, List<TaskProgress>> getCache() {
        return cache;
    }

    public Set<UUID> getDirtyEntries() {
        return dirtyEntries;
    }

    /**
     * 获取所有可能完成任务的玩家
     *
     * @return {@link Set }<{@link TaskProgress }>
     */
    public Set<TaskProgress> getMaybeFinishedPlayers() {
        return maybeFinishedEntries;
    }

    /**
     * 添加可能完成任务的玩家
     *
     * @param taskProgress 玩家任务
     */
    public void addMaybeFinishedPlayer(TaskProgress taskProgress) {
        maybeFinishedEntries.add(taskProgress);
    }

}