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

    public TaskManager(TaskStorage taskStorage, ProgressStorage progressStorage) {
        this.taskStorage = taskStorage;
        this.progressStorage = progressStorage;
    }

    public void setQuestEngine(QuestEngine questEngine) {
        this.questEngine = questEngine;
    }

    public void loadTasks() {
        taskStorage.findAll().forEach(task -> taskCache.put(task.getId(), task));
    }

    public Collection<TaskDefinition> getAllTasks() {
        return taskCache.values();
    }

    public Optional<TaskDefinition> getTask(String taskId) {
        return Optional.ofNullable(taskCache.get(taskId));
    }

    public void saveTask(TaskDefinition task) {
        taskStorage.save(task);
        taskCache.put(task.getId(), task);
    }

    public void deleteTask(String taskId) {
        taskStorage.delete(taskId);
        taskCache.remove(taskId);
    }

    public List<TaskDefinition> getAvailableTasks(Player player) {
        return taskCache.values().stream()
            .filter(task -> task.getConditions().stream().allMatch(c -> c.isMet(player)))
            .toList();
    }

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

    public void handleEvent(Player player, Event event) {
        if (questEngine != null) {
            questEngine.handleEvent(player, event);
        }
    }

    private void asyncSaveProgress(UUID playerId, TaskProgress progress) {
        progressStorage.save(playerId, progress);
    }

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

    public TaskProgress getProgress(UUID playerId, String taskId) {
        return playerProgressCache.computeIfAbsent(playerId, k -> {
            Map<String, TaskProgress> map = new HashMap<>();
            progressStorage.findByPlayer(playerId).forEach(p -> map.put(p.getTaskId(), p));
            return map;
        }).get(taskId);
    }

    public void loadPlayerProgress(UUID playerId) {
        Map<String, TaskProgress> map = new HashMap<>();
        progressStorage.findByPlayer(playerId).forEach(p -> map.put(p.getTaskId(), p));
        playerProgressCache.put(playerId, map);
    }

    public boolean abandonTask(Player player, String taskId) {
        Map<String, TaskProgress> progressMap = playerProgressCache.get(player.getUniqueId());
        TaskProgress progress = progressMap != null ? progressMap.get(taskId) : null;
        if (progress == null) return false;
        
        progress.setStatus(PTXTaskStatus.ABANDONED);
        progressStorage.save(player.getUniqueId(), progress);
        return true;
    }

    public Collection<TaskProgress> getAllProgress() {
        return playerProgressCache.values().stream()
            .flatMap(map -> map.values().stream())
            .toList();
    }

    public Map<UUID, Map<String, TaskProgress>> getPlayerProgressCache() {
        return playerProgressCache;
    }
}
