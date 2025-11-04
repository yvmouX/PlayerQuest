package com.playerPlugin.playerTaskX.cache;

import cn.yvmou.ylib.api.scheduler.UniversalTask;
import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.PlayerTask.Task.Task;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import com.playerPlugin.playerTaskX.dataManager.dao.PlayerTaskDAO;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
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
public class PlayerTaskCache {
    private static volatile PlayerTaskCache instance;
    
    // 缓存配置
    private static final int MAX_CACHE_SIZE = 100;
    private static final long SAVE_INTERVAL_SECONDS = 30 * 20; // 30秒保存一次
    private static final long CLEANUP_INTERVAL_SECONDS = 300; // 5分钟清理一次过期缓存
    
    // LRU缓存 - 使用LinkedHashMap实现LRU策略
    private final Map<UUID, List<PlayerTask>> cache;
    
    // 脏数据标记 - 记录需要保存到数据库的数据
    private final Set<UUID> dirtyEntries = ConcurrentHashMap.newKeySet();

    private List<UniversalTask> universalTask;

    
    private PlayerTaskCache() {
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
        // 启动定时保存任务 每30秒保存一次脏数据到数据库
        UniversalTask universalTask1 = PlayerTaskX.getYLib().getScheduler().runTimer(() -> {
            if (dirtyEntries.isEmpty()) {
                return;
            }

            // 复制一份数据集合，避免并发修改
            Set<UUID> currentDirty = new HashSet<>(dirtyEntries);

            // 异步保存到数据库
            PlayerTaskX.getYLib().getScheduler().runAsync(() -> {
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
        UniversalTask universalTask2 = PlayerTaskX.getYLib().getScheduler().runTimer(() -> {
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
     * 初始化缓存管理器
     * @param plugin 插件实例
     */
    public static void init(PlayerTaskX plugin) {
        if (plugin == null) {
            throw new NullPointerException("plugin can't be null");
        }
        
        if (instance == null) {
            synchronized (PlayerTaskCache.class) {
                if (instance == null) {
                    instance = new PlayerTaskCache();
                }
            }
        }
    }
    
    /**
     * 获取缓存管理器实例
     * @return 缓存管理器实例
     */
    public static PlayerTaskCache getInstance() {
        if (instance == null) {
            throw new IllegalStateException("PlayerTaskCache not initialized");
        }
        return instance;
    }


    /**
     * 获取指定玩家进行中的任务ID列表
     * 优先从缓存中过滤进行中的任务；如果缓存不存在则从数据库查询
     * @param uuid 玩家UUID
     * @return 进行中任务ID列表
     */
    public List<String> getPlayerInProgressTaskIds(UUID uuid) {
        List<PlayerTask> tasks = cache.get(uuid);
        if (tasks != null) {
            return tasks.stream()
                    .filter(pt -> pt.getStatus() == PTXTaskStatus.IN_PROGRESS)
                    .map(pt -> pt.getTask().getId())
                    .toList();
        }

        try {
            return StorgeManager.getPlayerTaskDAO().getInProgressTaskIds(uuid.toString());
        } catch (SQLException e) {
            PlayerTaskX.getYLib().getLoggerTools().error("从数据库加载进行中任务失败：" + uuid, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取指定玩家进行中的任务列表
     * 优先从缓存中过滤进行中的任务；如果缓存不存在则从数据库查询
     * @param uuid 玩家UUID
     * @return 进行中任务列表
     */
    @Nullable
    @org.jetbrains.annotations.Nullable
    public List<PlayerTask> getPlayerInProgressTasks(UUID uuid) {
        return cache.get(uuid);
    }
    
    /**
     * 更新玩家任务 / 添加新任务
     *
     * <p>
     *     如果缓存中不存在该玩家的任务列表，则添加新任务到缓存列表中
     *     如果缓存中存在该玩家的任务列表，则更新该任务
     * </p>
     *
     * @param playerTask 玩家任务
     * @param immediateSave 是否立即保存到数据库
     */
    public void updatePlayerTaskToCache(PlayerTask playerTask, boolean immediateSave) {
        UUID uuid = playerTask.getUUID();

        // 如果缓存中不存在该玩家的任务列表，则创建一个新的
        List<PlayerTask> tasks = cache.computeIfAbsent(uuid, k -> new ArrayList<>());

        // 更新或添加任务
        boolean taskFound = false;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getTask().getId().equals(playerTask.getTask().getId())) {
                tasks.set(i, playerTask); // 更新现有任务
                taskFound = true;
                break;
            }
        }

        if (!taskFound) {
            tasks.add(playerTask); // 添加新任务
        }

        // 标记为脏数据，稍后保存到数据库
        dirtyEntries.add(uuid);

        // 如果立即保存，立即保存到数据库
        if (immediateSave) {
            savePlayerTasks(Collections.singletonList(playerTask));
        }
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
     * @param playerTasks 任务
     */
    private void savePlayerTasks(List<PlayerTask> playerTasks) {
        if (playerTasks == null || playerTasks.isEmpty()) {
            return;
        }

        try {
            StorgeManager.getPlayerTaskDAO().updateTasks(playerTasks);
        } catch (SQLException e) {
            PlayerTaskX.getYLib().getLoggerTools().error("批量保存玩家任务到数据库失败", e);
        }
    }


}