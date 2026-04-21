package com.playerPlugin.playerTaskX.manager;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;
import com.playerPlugin.playerTaskX.engine.QuestEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.*;

public class TaskManager {
    private final TaskStorage taskStorage;
    private final ProgressStorage progressStorage;
    private final Map<String, TaskDefinition> taskCache = new HashMap<>();
    private final Map<UUID, Map<String, TaskProgress>> playerProgressCache = new HashMap<>();
    private QuestEngine questEngine;

    /**
     * 构造任务管理器
     * @param taskStorage 任务存储服务
     * @param progressStorage 进度存储服务
     */
    public TaskManager(TaskStorage taskStorage, ProgressStorage progressStorage) {
        this.taskStorage = taskStorage;
        this.progressStorage = progressStorage;
    }

    /**
     * 设置任务引擎，用于处理事件和执行任务图
     * @param questEngine 任务引擎实例
     */
    public void setQuestEngine(QuestEngine questEngine) {
        this.questEngine = questEngine;
    }

    /**
     * 从存储加载所有任务到缓存
     */
    public void loadTasks() {
        taskStorage.findAll().forEach(task -> taskCache.put(task.getId(), task));
    }

    /**
     * 获取所有已加载的任务
     * @return 任务定义集合
     */
    public Collection<TaskDefinition> getAllTasks() {
        return taskCache.values();
    }

    /**
     * 根据ID获取任务
     * @param taskId 任务ID
     * @return 任务定义，若不存在则返回空Optional
     */
    public Optional<TaskDefinition> getTask(String taskId) {
        return Optional.ofNullable(taskCache.get(taskId));
    }

    /**
     * 保存或更新任务
     * @param task 要保存的任务定义
     */
    public void saveTask(TaskDefinition task) {
        taskStorage.save(task);
        taskCache.put(task.getId(), task);
    }

    /**
     * 删除任务
     * @param taskId 要删除的任务ID
     */
    public void deleteTask(String taskId) {
        taskStorage.delete(taskId);
        taskCache.remove(taskId);
    }

    /**
     * 获取玩家可接取的任务（所有条件都满足）
     * @param player 玩家
     * @return 可接取的任务列表
     */
    public List<TaskDefinition> getAvailableTasks(Player player) {
        return taskCache.values().stream()
            .filter(task -> task.getConditions().stream().allMatch(c -> c.isMet(player)))
            .toList();
    }

    /**
     * 玩家接取任务
     * @param player 玩家
     * @param taskId 任务ID
     * @return 是否接取成功（任务存在且条件满足返回true）
     */
    public boolean acceptTask(Player player, String taskId) {
        TaskDefinition task = taskCache.get(taskId);
        if (task == null) return false;
        if (task.getConditions().stream().anyMatch(c -> !c.isMet(player))) {
            return false;
        }
        
        TaskProgress progress = new TaskProgress(player.getUniqueId(), taskId);
        progressStorage.save(player.getUniqueId(), progress);
        playerProgressCache.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
            .put(taskId, progress);
        
        if (questEngine != null && task.hasGraph()) {
            Player actualPlayer = Bukkit.getPlayer(player.getUniqueId());
            if (actualPlayer != null) {
                questEngine.startQuest(actualPlayer, task);
            }
        }
        
        return true;
    }

    /**
     * 处理玩家触发的事件，转发给任务引擎
     * @param player 事件触发玩家
     * @param event 触发的事件
     */
    public void handleEvent(Player player, Event event) {
        if (questEngine != null) {
            questEngine.handleEvent(player, event);
        }
    }

    /**
     * 异步保存玩家进度到存储
     * @param playerId 玩家UUID
     * @param progress 任务进度
     */
    private void asyncSaveProgress(UUID playerId, TaskProgress progress) {
        progressStorage.save(playerId, progress);
    }

    /**
     * 玩家领取任务奖励
     * @param player 玩家
     * @param taskId 任务ID
     * @return 是否领取成功（任务完成且未领取过返回true）
     */
    public boolean claimReward(Player player, String taskId) {
        Map<String, TaskProgress> progressMap = playerProgressCache.get(player.getUniqueId());
        TaskProgress progress = progressMap != null ? progressMap.get(taskId) : null;
        if (progress == null || !progress.isCompleted()) return false;

        TaskDefinition task = taskCache.get(taskId);
        if (task == null) return false;

        progress.setStatus(PTXTaskStatus.CLAIMED);
        progress.setClaimedAt(System.currentTimeMillis());
        progressStorage.save(player.getUniqueId(), progress);
        return true;
    }

    /**
     * 获取玩家的指定任务进度
     * @param playerId 玩家UUID
     * @param taskId 任务ID
     * @return 任务进度，若不存在返回null
     */
    public TaskProgress getProgress(UUID playerId, String taskId) {
        return playerProgressCache.computeIfAbsent(playerId, k -> {
            Map<String, TaskProgress> map = new HashMap<>();
            progressStorage.findByPlayer(playerId).forEach(p -> map.put(p.getTaskId(), p));
            return map;
        }).get(taskId);
    }

    /**
     * 加载指定玩家的所有任务进度到缓存
     * @param playerId 玩家UUID
     */
    public void loadPlayerProgress(UUID playerId) {
        Map<String, TaskProgress> map = new HashMap<>();
        progressStorage.findByPlayer(playerId).forEach(p -> map.put(p.getTaskId(), p));
        playerProgressCache.put(playerId, map);
    }

    /**
     * 玩家放弃任务
     * @param player 玩家
     * @param taskId 任务ID
     * @return 是否放弃成功（进度存在返回true）
     */
    public boolean abandonTask(Player player, String taskId) {
        Map<String, TaskProgress> progressMap = playerProgressCache.get(player.getUniqueId());
        TaskProgress progress = progressMap != null ? progressMap.get(taskId) : null;
        if (progress == null) return false;
        
        progress.setStatus(PTXTaskStatus.ABANDONED);
        progressStorage.save(player.getUniqueId(), progress);
        return true;
    }

    /**
     * 获取所有玩家的所有任务进度
     * @return 所有进度的集合
     */
    public Collection<TaskProgress> getAllProgress() {
        return playerProgressCache.values().stream()
            .flatMap(map -> map.values().stream())
            .toList();
    }

    /**
     * 获取玩家进度缓存的直接引用（用于批量操作）
     * @return 缓存映射表
     */
    public Map<UUID, Map<String, TaskProgress>> getPlayerProgressCache() {
        return playerProgressCache;
    }
}
