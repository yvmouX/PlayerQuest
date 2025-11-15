package com.playerPlugin.playerTaskX.dataManager.cache;

import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.playerPlugin.playerTaskX.utils.Help.log;


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

    // LRU缓存 - 使用LinkedHashMap实现LRU策略
    private Map<UUID, List<PlayerTask>> cache;

    // 脏数据标记 - 记录需要保存到数据库的数据
    private final Set<UUID> dirtyEntries = ConcurrentHashMap.newKeySet();
    private final Set<PlayerTask> maybeFinishedEntries = ConcurrentHashMap.newKeySet();

    public PlayerTaskCache() {
    }

    public void init(Map<UUID, List<PlayerTask>> playerTaskCache) {
        this.cache = playerTaskCache;
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

}