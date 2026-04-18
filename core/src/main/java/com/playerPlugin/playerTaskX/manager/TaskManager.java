package com.playerPlugin.playerTaskX.manager;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.api.model.objective.Objective;
import com.playerPlugin.playerTaskX.api.model.reward.Reward;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.*;

public class TaskManager {
    private final TaskStorage taskStorage;
    private final ProgressStorage progressStorage;
    private final Map<String, TaskDefinition> taskCache = new HashMap<>();
    private final Map<UUID, Map<String, TaskProgress>> playerProgressCache = new HashMap<>();

    public TaskManager(TaskStorage taskStorage, ProgressStorage progressStorage) {
        this.taskStorage = taskStorage;
        this.progressStorage = progressStorage;
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
        return true;
    }

    public void handleEvent(Player player, Event event) {
        Map<String, TaskProgress> progressMap = playerProgressCache.get(player.getUniqueId());
        if (progressMap == null) return;

        for (TaskProgress progress : progressMap.values()) {
            if (progress.getStatus() != PTXTaskStatus.IN_PROGRESS) continue;
            
            TaskDefinition task = taskCache.get(progress.getTaskId());
            if (task == null) continue;

            for (Objective objective : task.getObjectives()) {
                if (objective.matchesEvent(event)) {
                    objective.applyProgress(player, 1);
                    int currentProgress = progress.getProgress(objective.getId());
                    progress.setProgress(objective.getId(), currentProgress + 1);
                    
                    if (objective.isCompleted(player)) {
                        boolean allCompleted = task.getObjectives().stream()
                            .allMatch(obj -> obj.isCompleted(player));
                        if (allCompleted) {
                            progress.setStatus(PTXTaskStatus.COMPLETED);
                            progress.setCompletedAt(System.currentTimeMillis());
                        }
                    }
                    
                    asyncSaveProgress(player.getUniqueId(), progress);
                }
            }
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

        for (Reward reward : task.getRewards()) {
            reward.grant(player);
        }

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
