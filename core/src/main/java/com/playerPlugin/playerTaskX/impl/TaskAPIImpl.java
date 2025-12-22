package com.playerPlugin.playerTaskX.impl;

import cn.yvmou.ylib.api.services.LoggerService;
import com.playerPlugin.playerTaskX.api.TaskAPI;
import com.playerPlugin.playerTaskX.api.event.TaskEventListener;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskObjective;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.storage.TaskRepository;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public class TaskAPIImpl implements TaskAPI {
    private final LoggerService log;
    private final TaskRepository taskRepo;
    private final TaskProgressRepository progressRepo;
    private final TaskCache cache;

    public TaskAPIImpl(LoggerService log, TaskRepository taskRepo, TaskProgressRepository progressRepo, TaskCache cache) {
        this.log = log;
        this.taskRepo = taskRepo;
        this.progressRepo = progressRepo;
        this.cache = cache;
    }

    @Override
    public boolean createTask(TaskDefinition taskDef) {
        try {
            if (taskDef.getId() == null || taskDef.getId().isEmpty()) {
                log.error("Task ID cannot be null or empty");
                return false;
            }
            if (taskRepo.findById(taskDef.getId()).isPresent()) {
                log.error("Task with ID " + taskDef.getId() + " already exists");
                return false;
            }

            // todo 只保存到仓库  暂时
            taskRepo.save(taskDef);
            log.info("Task with ID " + taskDef.getId() + " created successfully");
            return true;
        } catch (Exception e) {
            log.error("Error creating task", e);
            return false;
        }
    }

    @Override
    public boolean deleteTask(String taskId) {
        // TODO
        return false;
    }

    @Override
    public boolean createProgress(Player player, String taskId) {
        try {
            Optional<TaskDefinition> taskDefOpt = taskRepo.findById(taskId);
            if (taskDefOpt.isEmpty()) {
                log.error("Task with ID " + taskId + " does not exist");
                return false;
            }

            progressRepo.createForPlayer(player, taskDefOpt.get());

            // Trigger task start Event
            fireTaskStartEvent(player.getUniqueId(), taskId);

            log.info("Progress for task " + taskId + " created successfully");
            return true;
        } catch (Exception e) {
            log.error("Error creating task", e);
            return false;
        }
    }

    @Override
    public boolean deleteProgress(Player player, String taskId) {
        // TODO
        return false;
    }

    @Override
    public boolean updateProgress(Player player, String taskId, int progress) {
        try {
//            Optional<TaskProgress> progressOpt = progressRepo.findByPlayerAndTask(player, taskId);
//            if (progressOpt.isEmpty()) {
//                log.error("Task progress not found for player " + player.getUniqueId() + " and task " + taskId);
//                return false;
//            }
//
//            TaskProgress taskProgress = progressOpt.get();
//            for (TaskObjective objective : taskProgress.getTaskDefinition().getObjectives()) {
//                int oldProgress = objective.getCurrentAmount();
//                progressRepo.update();
//
//                // 获取目标进度
//
//            }
            //taskProgress.setCurrentProgress(progress);
            //progressRepo.update(taskProgress);

            // 获取目标进度
            //var taskDefOpt = taskRepo.findById(taskId);
            //int targetProgress = taskDefOpt.map(TaskDefinition::getTargetAmount).orElse(0);

            // 触发进度更新事件
            //fireTaskProgressEvent(player.getUniqueId(), taskId, oldProgress, progress, targetProgress);

            return true;
        } catch (Exception e) {
            log.error("Error updating task progress", e);
            return false;
        }
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
//        if (!eventListeners.contains(listener)) {
//            eventListeners.add(listener);
//            log.debug("Task event listener registered: " + listener.getClass().getName());
//        }
    }

    @Override
    public void unregisterEventListener(TaskEventListener listener) {
//        eventListeners.remove(listener);
//        log.debug("Task event listener unregistered: " + listener.getClass().getName());
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

    // 事件触发方法
    private void fireTaskStartEvent(UUID playerId, String taskId) {
//        TaskStartEvent event = new TaskStartEvent(playerId, taskId);
//        for (TaskEventListener listener : eventListeners) {
//            try {
//                listener.onTaskStart(event);
//            } catch (Exception e) {
//                log.error("Error in task start event listener: " + e.getMessage());
//            }
//        }
    }

    private void fireTaskProgressEvent(UUID playerId, String taskId, int oldProgress, int newProgress, int targetProgress) {
//        TaskProgressEvent event = new TaskProgressEvent(playerId, taskId, oldProgress, newProgress, targetProgress);
//        for (TaskEventListener listener : eventListeners) {
//            try {
//                listener.onTaskProgress(event);
//            } catch (Exception e) {
//                log.error("Error in task progress event listener: " + e.getMessage());
//            }
//        }
    }

    private void fireTaskCompleteEvent(UUID playerId, String taskId, long completionTime) {
//        TaskCompleteEvent event = new TaskCompleteEvent(playerId, taskId, completionTime);
//        for (TaskEventListener listener : eventListeners) {
//            try {
//                listener.onTaskComplete(event);
//            } catch (Exception e) {
//                log.error("Error in task complete event listener: " + e.getMessage());
//            }
//        }
    }

    public void fireTaskFailEvent(UUID playerId, String taskId, String reason) {
//        TaskFailEvent event = new TaskFailEvent(playerId, taskId, reason);
//        for (TaskEventListener listener : eventListeners) {
//            try {
//                listener.onTaskFail(event);
//            } catch (Exception e) {
//                log.error("Error in task fail event listener: " + e.getMessage());
//            }
//        }
    }
}
