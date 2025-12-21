package com.playerPlugin.playerTaskX.service;

import cn.yvmou.ylib.api.services.LoggerService;
import com.playerPlugin.playerTaskX.api.PlayerTaskXAPI;
import com.playerPlugin.playerTaskX.api.event.*;
import com.playerPlugin.playerTaskX.api.task.ITaskDefinition;
import com.playerPlugin.playerTaskX.api.task.ITaskProgress;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.model.TaskDefinition;
import com.playerPlugin.playerTaskX.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.storage.TaskRepository;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PlayerTaskXAPI 的实现类
 */
public class PlayerTaskXAPIImpl implements PlayerTaskXAPI {
    private static final String API_VERSION = "1.0.0";
    
    private final LoggerService log;
    private final TaskCache cache;
    private final TaskRepository taskRepository;
    private final TaskProgressRepository progressRepository;
    private final List<TaskEventListener> eventListeners;

    public PlayerTaskXAPIImpl(LoggerService log, TaskCache cache,
                              TaskRepository taskRepository, 
                              TaskProgressRepository progressRepository) {
        this.log = log;
        this.cache = cache;
        this.taskRepository = taskRepository;
        this.progressRepository = progressRepository;
        this.eventListeners = new ArrayList<>();
    }

    @Override
    public boolean createTask(ITaskDefinition taskDefinition) {
        try {
            if (!(taskDefinition instanceof TaskDefinition)) {
                log.error("Invalid task definition type");
                return false;
            }
            
            TaskDefinition taskDef = (TaskDefinition) taskDefinition;
            
            if (taskDef.getId() == null || taskDef.getId().isEmpty()) {
                log.error("Task ID cannot be null or empty");
                return false;
            }
            
            if (taskRepository.findById(taskDef.getId()).isPresent()) {
                log.error("Task with ID " + taskDef.getId() + " already exists");
                return false;
            }
            
            taskRepository.save(taskDef);
            //cache.setTaskDefById().add(taskDef.getId(), taskDef);
            log.info("Task " + taskDef.getId() + " created successfully");
            return true;
        } catch (Exception e) {
            log.error("Failed to create task: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteTask(String taskId) {
        try {
            if (taskRepository.findById(taskId).isEmpty()) {
                log.error("Task with ID " + taskId + " not found");
                return false;
            }
            
            //taskRepository.deleteById(taskId);
            cache.setTaskDefById().remove(taskId);
            log.info("Task " + taskId + " deleted successfully");
            return true;
        } catch (Exception e) {
            log.error("Failed to delete task: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Optional<ITaskDefinition> getTaskDefinition(String taskId) {
        return taskRepository.findById(taskId).map(task -> (ITaskDefinition) task);
    }

    @Override
    public List<ITaskDefinition> getAllTaskDefinitions() {
        return taskRepository.loadAll().stream()
                .map(task -> (ITaskDefinition) task)
                .collect(Collectors.toList());
    }

    @Override
    public boolean createTaskProgress(Player player, String taskId) {
        try {
            Optional<TaskDefinition> taskDefOpt = taskRepository.findById(taskId);
            if (taskDefOpt.isEmpty()) {
                log.error("Task with ID " + taskId + " not found");
                return false;
            }
            
            progressRepository.createForPlayer(player, taskDefOpt.get());
            
            // 触发任务开始事件
            fireTaskStartEvent(player.getUniqueId(), taskId);
            
            log.info("Task progress created for player " + player.getName() + " on task " + taskId);
            return true;
        } catch (Exception e) {
            log.error("Failed to create task progress: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Optional<ITaskProgress> getTaskProgress(UUID playerId, String taskId) {
//        return progressRepository.findByPlayerAndTask(playerId, taskId)
//                .map(progress -> (ITaskProgress) new TaskProgressAdapter(progress));
        return Optional.empty();
    }

    @Override
    public List<ITaskProgress> getAllTaskProgress(UUID playerId) {
//        return progressRepository.findByPlayer(playerId).stream()
//                .map(progress -> (ITaskProgress) new TaskProgressAdapter(progress))
//                .collect(Collectors.toList());
        return Collections.emptyList();
    }

    @Override
    public boolean updateTaskProgress(UUID playerId, String taskId, int progress) {
//        try {
//            var progressOpt = progressRepository.findByPlayerAndTask(playerId, taskId);
//            if (progressOpt.isEmpty()) {
//                log.error("Task progress not found for player " + playerId + " and task " + taskId);
//                return false;
//            }
//
//            var taskProgress = progressOpt.get();
//            int oldProgress = taskProgress.getCurrentProgress();
//            taskProgress.setCurrentProgress(progress);
//            progressRepository.update(taskProgress);
//
//            // 获取目标进度
//            var taskDefOpt = taskRepository.findById(taskId);
//            int targetProgress = taskDefOpt.map(TaskDefinition::getTargetAmount).orElse(0);
//
//            // 触发进度更新事件
//            fireTaskProgressEvent(playerId, taskId, oldProgress, progress, targetProgress);
//
//            return true;
//        } catch (Exception e) {
//            log.error("Failed to update task progress: " + e.getMessage());
//            return false;
//        }
        return false;
    }

    @Override
    public boolean incrementTaskProgress(UUID playerId, String taskId, int amount) {
//        try {
//            var progressOpt = progressRepository.findByPlayerAndTask(playerId, taskId);
//            if (progressOpt.isEmpty()) {
//                log.error("Task progress not found for player " + playerId + " and task " + taskId);
//                return false;
//            }
//
//            var taskProgress = progressOpt.get();
//            int oldProgress = taskProgress.getCurrentProgress();
//            int newProgress = oldProgress + amount;
//            taskProgress.setCurrentProgress(newProgress);
//            progressRepository.update(taskProgress);
//
//            // 获取目标进度
//            var taskDefOpt = taskRepository.findById(taskId);
//            int targetProgress = taskDefOpt.map(TaskDefinition::getTargetAmount).orElse(0);
//
//            // 触发进度更新事件
//            fireTaskProgressEvent(playerId, taskId, oldProgress, newProgress, targetProgress);
//
//            return true;
//        } catch (Exception e) {
//            log.error("Failed to increment task progress: " + e.getMessage());
//            return false;
//        }
        return false;
    }

    @Override
    public boolean completeTask(UUID playerId, String taskId) {
//        try {
//            var progressOpt = progressRepository.findByPlayerAndTask(playerId, taskId);
//            if (progressOpt.isEmpty()) {
//                log.error("Task progress not found for player " + playerId + " and task " + taskId);
//                return false;
//            }
//
//            var taskProgress = progressOpt.get();
//            taskProgress.setStatus(com.playerPlugin.playerTaskX.common.Enum.PTXTaskStatus.COMPLETED);
//            progressRepository.update(taskProgress);
//
//            // 触发任务完成事件
//            fireTaskCompleteEvent(playerId, taskId, System.currentTimeMillis());
//
//            log.info("Task " + taskId + " completed for player " + playerId);
//            return true;
//        } catch (Exception e) {
//            log.error("Failed to complete task: " + e.getMessage());
//            return false;
//        }
        return false;
    }

    @Override
    public boolean resetTask(UUID playerId, String taskId) {
//        try {
//            var progressOpt = progressRepository.findByPlayerAndTask(playerId, taskId);
//            if (progressOpt.isEmpty()) {
//                log.error("Task progress not found for player " + playerId + " and task " + taskId);
//                return false;
//            }
//
//            var taskProgress = progressOpt.get();
//            taskProgress.setCurrentProgress(0);
//            taskProgress.setStatus(com.playerPlugin.playerTaskX.common.Enum.PTXTaskStatus.IN_PROGRESS);
//            progressRepository.update(taskProgress);
//
//            log.info("Task " + taskId + " reset for player " + playerId);
//            return true;
//        } catch (Exception e) {
//            log.error("Failed to reset task: " + e.getMessage());
//            return false;
//        }
        return false;
    }

    @Override
    public boolean isTaskCompleted(UUID playerId, String taskId) {
//        var progressOpt = progressRepository.findByPlayerAndTask(playerId, taskId);
//        if (progressOpt.isEmpty()) {
//            return false;
//        }
//
//        return progressOpt.get().getStatus() == com.playerPlugin.playerTaskX.common.Enum.PTXTaskStatus.COMPLETED;
        return false;
    }

    @Override
    public void registerEventListener(TaskEventListener listener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener);
            log.debug("Task event listener registered: " + listener.getClass().getName());
        }
    }

    @Override
    public void unregisterEventListener(TaskEventListener listener) {
        eventListeners.remove(listener);
        log.debug("Task event listener unregistered: " + listener.getClass().getName());
    }

    @Override
    public void reload() {
//        try {
//            // 重新加载任务定义
//            List<TaskDefinition> taskDefList = taskRepository.loadAll();
//            cache.setTaskDefById().clear();
//            for (TaskDefinition taskDef : taskDefList) {
//                cache.setTaskDefById().add(taskDef.getId(), taskDef);
//            }
//            log.info("Tasks reloaded successfully. Total: " + taskDefList.size());
//        } catch (Exception e) {
//            log.error("Failed to reload tasks: " + e.getMessage());
//        }
    }

    @Override
    public String getApiVersion() {
        return API_VERSION;
    }

    // 事件触发方法
    private void fireTaskStartEvent(UUID playerId, String taskId) {
        TaskStartEvent event = new TaskStartEvent(playerId, taskId);
        for (TaskEventListener listener : eventListeners) {
            try {
                listener.onTaskStart(event);
            } catch (Exception e) {
                log.error("Error in task start event listener: " + e.getMessage());
            }
        }
    }

    private void fireTaskProgressEvent(UUID playerId, String taskId, int oldProgress, int newProgress, int targetProgress) {
        TaskProgressEvent event = new TaskProgressEvent(playerId, taskId, oldProgress, newProgress, targetProgress);
        for (TaskEventListener listener : eventListeners) {
            try {
                listener.onTaskProgress(event);
            } catch (Exception e) {
                log.error("Error in task progress event listener: " + e.getMessage());
            }
        }
    }

    private void fireTaskCompleteEvent(UUID playerId, String taskId, long completionTime) {
        TaskCompleteEvent event = new TaskCompleteEvent(playerId, taskId, completionTime);
        for (TaskEventListener listener : eventListeners) {
            try {
                listener.onTaskComplete(event);
            } catch (Exception e) {
                log.error("Error in task complete event listener: " + e.getMessage());
            }
        }
    }

    public void fireTaskFailEvent(UUID playerId, String taskId, String reason) {
        TaskFailEvent event = new TaskFailEvent(playerId, taskId, reason);
        for (TaskEventListener listener : eventListeners) {
            try {
                listener.onTaskFail(event);
            } catch (Exception e) {
                log.error("Error in task fail event listener: " + e.getMessage());
            }
        }
    }
}
