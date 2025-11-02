package com.playerPlugin.playerTaskX.cache;

import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 玩家任务缓存管理器
 * 结合LRU缓存策略和写入缓冲区，提高性能和可靠性
 *
 * <p>
 *     1.只保存在线玩家的任务数据
 *     2.定期为离线玩家的任务数据保存到数据库
 * </p>
 */
public class PlayerTaskCache {
    private static volatile PlayerTaskCache instance;
    private final PlayerTaskX plugin;
    
    // 缓存配置
    private static final int MAX_CACHE_SIZE = 100;
    private static final long SAVE_INTERVAL_SECONDS = 30 * 20; // 30秒保存一次
    private static final long CLEANUP_INTERVAL_SECONDS = 300; // 5分钟清理一次过期缓存
    
    // LRU缓存 - 使用LinkedHashMap实现LRU策略
    private final Map<UUID, List<PlayerTask>> cache;
    
    // 脏数据标记 - 记录需要保存到数据库的数据
    private final Set<UUID> dirtyEntries = ConcurrentHashMap.newKeySet();
    
    // 定时任务执行器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private PlayerTaskCache(PlayerTaskX plugin) {
        this.plugin = plugin;
        
        // 初始化LRU缓存
        this.cache = Collections.synchronizedMap(new LinkedHashMap<UUID, List<PlayerTask>>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, List<PlayerTask>> eldest) {
                // 当缓存超过最大容量时，移除最久未使用的条目
                // 但如果是脏数据，先保存到数据库
                if (size() > MAX_CACHE_SIZE) {
                    if (dirtyEntries.contains(eldest.getKey())) {
                        savePlayerTasks(eldest.getKey(), eldest.getValue());
                        dirtyEntries.remove(eldest.getKey());
                    }
                    return true;
                }
                return false;
            }
        });
        
        // 启动定时保存任务
        PlayerTaskX.getYLib().getScheduler().runTimer(this::saveAllDirty, 0, SAVE_INTERVAL_SECONDS);
        
        // 启动定时清理任务
        PlayerTaskX.getYLib().getScheduler().runTimer(this::cleanupOfflinePlayers, 0, CLEANUP_INTERVAL_SECONDS);
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
                    instance = new PlayerTaskCache(plugin);
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
     * 获取玩家任务列表
     * 优先从缓存获取，缓存没有则从数据库加载
     * @param uuid 玩家UUID
     * @return 玩家任务列表
     */
    public List<PlayerTask> getPlayerTasks(UUID uuid) {
        // 从缓存获取
        List<PlayerTask> tasks = cache.get(uuid);
        if (tasks != null) {
            return new ArrayList<>(tasks); // 返回副本，避免外部修改缓存
        }
        
        // 缓存未命中，从数据库加载
        tasks = StorgeManager.getInstance().loadPlayerTasks(uuid);
        if (tasks != null) {
            cache.put(uuid, new ArrayList<>(tasks)); // 存入缓存
        } else {
            tasks = new ArrayList<>();
        }
        
        return tasks;
    }
    
    /**
     * 更新玩家任务
     * @param playerTask 玩家任务
     */
    public void updatePlayerTask(PlayerTask playerTask) {
        UUID uuid = playerTask.getUUID();
        List<PlayerTask> tasks = cache.computeIfAbsent(uuid, k -> new ArrayList<>());
        
        // 更新或添加任务
        boolean updated = false;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getTask().getId().equals(playerTask.getTask().getId())) {
                tasks.set(i, playerTask);
                updated = true;
                break;
            }
        }
        
        if (!updated) {
            tasks.add(playerTask);
        }
        
        // 标记为脏数据
        dirtyEntries.add(uuid);
    }
    
    /**
     * 添加新的玩家任务
     * @param playerTask 玩家任务
     */
    public void addPlayerTask(PlayerTask playerTask) {
        UUID uuid = playerTask.getUUID();
        List<PlayerTask> tasks = cache.computeIfAbsent(uuid, k -> new ArrayList<>());
        
        // 检查是否已存在相同任务
        boolean exists = tasks.stream()
                .anyMatch(pt -> pt.getTask().getId().equals(playerTask.getTask().getId()));
        
        if (!exists) {
            tasks.add(playerTask);
            dirtyEntries.add(uuid);
            
            // 立即保存到数据库
            StorgeManager.getInstance().createNewPlayer(playerTask);
        }
    }
    
    /**
     * 保存所有脏数据到数据库
     */
    private void saveAllDirty() {
        if (dirtyEntries.isEmpty()) {
            return;
        }
        
        // 复制一份脏数据集合，避免并发修改
        Set<UUID> currentDirty = new HashSet<>(dirtyEntries);
        
        // 异步保存到数据库
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (UUID uuid : currentDirty) {
                List<PlayerTask> tasks = cache.get(uuid);
                if (tasks != null) {
                    for (PlayerTask task : tasks) {
                        StorgeManager.getInstance().updatePlayerTask(task);
                    }
                    // 从脏数据集合中移除
                    dirtyEntries.remove(uuid);
                }
            }
        });
    }
    
    /**
     * 清理离线玩家的缓存
     */
    private void cleanupOfflinePlayers() {
        Set<UUID> cachedPlayers = new HashSet<>(cache.keySet());
        
        for (UUID uuid : cachedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                // 玩家离线，检查是否有脏数据需要保存
                if (dirtyEntries.contains(uuid)) {
                    savePlayerTasks(uuid, cache.get(uuid));
                    dirtyEntries.remove(uuid);
                }
                // 从缓存中移除
                cache.remove(uuid);
            }
        }
    }
    
    /**
     * 保存玩家任务到数据库
     * @param uuid 玩家UUID
     * @param tasks 任务列表
     */
    private void savePlayerTasks(UUID uuid, List<PlayerTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        
        for (PlayerTask task : tasks) {
            StorgeManager.getInstance().updatePlayerTask(task);
        }
    }
    
    /**
     * 关闭缓存管理器
     * 保存所有数据到数据库
     */
    public void shutdown() {
        // 保存所有缓存数据
        for (Map.Entry<UUID, List<PlayerTask>> entry : cache.entrySet()) {
            if (dirtyEntries.contains(entry.getKey())) {
                savePlayerTasks(entry.getKey(), entry.getValue());
            }
        }
        
        // 关闭调度器
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 重新加载玩家数据
     * @param uuid 玩家UUID
     */
    public void reloadPlayerData(UUID uuid) {
        // 如果有脏数据，先保存
        if (dirtyEntries.contains(uuid)) {
            savePlayerTasks(uuid, cache.get(uuid));
            dirtyEntries.remove(uuid);
        }
        
        // 从缓存中移除
        cache.remove(uuid);
        
        // 重新加载
        List<PlayerTask> tasks = StorgeManager.getInstance().loadPlayerTasks(uuid);
        if (tasks != null && !tasks.isEmpty()) {
            cache.put(uuid, tasks);
        }
    }
    
    /**
     * 获取当前缓存大小
     * @return 缓存大小
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * 获取脏数据数量
     * @return 脏数据数量
     */
    public int getDirtyEntriesCount() {
        return dirtyEntries.size();
    }
}