package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.api.logger.Logger;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.event.TaskEventListener;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskObjective;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.api.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.api.storage.TaskRepository;
import com.playerPlugin.playerTaskX.api.utils.TimeUtil;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.storage.StorageFactory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class TaskAPI {
    private final Logger log;
    private final TaskRepository taskRepo;
    private final TaskProgressRepository progressRepo;
    private final TaskCache cache;

    public TaskAPI(Logger log, StorageFactory storageFactory, TaskCache cache) {
        this.log = log;
        this.cache = cache;

        this.taskRepo = storageFactory.getRepository();
        this.progressRepo = storageFactory.getProgressRepository();
    }

    // --- Task Definition Management ---

    public boolean createTask(TaskDefinition taskDef) {
        return safeExecute("creating task", () -> {
            if (isInvalidId(taskDef.getId())) return false;
            
            if (taskRepo.findById(taskDef.getId()).isPresent()) {
                log.error("Task with ID " + taskDef.getId() + " already exists");
                return false;
            }

            taskRepo.save(taskDef);
            cache.addTaskDef(taskDef);
            log.info("Task with ID " + taskDef.getId() + " created successfully");
            return true;
        });
    }

    public boolean deleteTask(String taskID) {
        return safeExecute("deleting task", () -> {
            if (isInvalidId(taskID)) return false;

            TaskDefinition taskDef = taskRepo.findById(taskID).orElse(null);
            if (taskDef == null) {
                log.error("Task with ID " + taskID + " does not exist");
                return false;
            }

            taskRepo.delete(taskID);
            cache.removeTaskDef(taskDef);
            log.info("Task with ID " + taskDef.getId() + " deleted successfully");
            return true;
        });
    }

    // --- Progress Management ---

    public boolean createProgress(Player player, String taskId) {
        return safeExecute("creating progress", () -> {
            Optional<TaskDefinition> taskDefOpt = taskRepo.findById(taskId);
            if (taskDefOpt.isEmpty()) {
                log.error("Task with ID " + taskId + " does not exist");
                return false;
            }

            progressRepo.create(player, taskDefOpt.get());
            fireTaskStartEvent(player.getUniqueId(), taskId);
            log.info("Progress for task " + taskId + " created successfully");
            return true;
        });
    }

    public boolean deleteProgress(Player player, String taskId) {
        return safeExecute("deleting task progress", () -> {
            if (getTaskProgress(player, taskId).isEmpty()) return false;
            
            progressRepo.delete(player.getUniqueId(), taskId);
            log.info("Task progress deleted for player " + player.getName() + " task " + taskId);
            return true;
        });
    }

    public boolean updateProgress(Player player, String taskId, int progress) {
        return executeProgressUpdate(player, taskId, (currentProgress) -> {
            return calculateNewState(currentProgress, (objective) -> {
                int target = objective.getTargetAmount();
                int newAmount = Math.min(progress, target);
                return createObjective(objective, newAmount, newAmount >= target);
            });
        }, null);
    }

    public boolean incrementTaskProgress(UUID playerId, String taskId, int amount) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;

        return executeProgressUpdate(player, taskId, (currentProgress) -> {
            return calculateNewState(currentProgress, (objective) -> {
                int target = objective.getTargetAmount();
                int newAmount = Math.min(objective.getCurrentAmount() + amount, target);
                return createObjective(objective, newAmount, newAmount >= target);
            });
        }, null);
    }

    public boolean completeTask(UUID playerId, String taskId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;

        return executeProgressUpdate(player, taskId, (currentProgress) -> {
            List<TaskObjective> newObjectives = mapObjectives(currentProgress, 
                obj -> createObjective(obj, obj.getTargetAmount(), true));
            return new UpdateResult(newObjectives, PTXTaskStatus.COMPLETED);
        }, (uuid, id) -> fireTaskCompleteEvent(uuid, id, System.currentTimeMillis()));
    }

    public boolean resetTask(UUID playerId, String taskId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;

        return executeProgressUpdate(player, taskId, (currentProgress) -> {
            List<TaskObjective> newObjectives = mapObjectives(currentProgress, 
                obj -> createObjective(obj, 0, false));
            return new UpdateResult(newObjectives, PTXTaskStatus.IN_PROGRESS);
        }, this::fireTaskStartEvent);
    }

    public boolean isTaskCompleted(UUID playerId, String taskId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;
        
        return progressRepo.find(player, taskId)
                .map(p -> p.getStatus() == PTXTaskStatus.COMPLETED)
                .orElse(false);
    }

    public List<TaskProgress> getPlayerTasks(Player player) {
        return progressRepo.findAll(player);
    }

    private boolean isInvalidId(String id) {
        if (id == null || id.isEmpty()) {
            log.error("Task ID cannot be null or empty");
            return true;
        }
        return false;
    }

    private boolean safeExecute(String actionName, BooleanSupplier action) {
        try {
            return action.getAsBoolean();
        } catch (Exception e) {
            log.error("Error " + actionName, e);
            return false;
        }
    }
    
    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }

    private Optional<TaskProgress> getTaskProgress(Player player, String taskId) {
        Optional<TaskProgress> progressOpt = progressRepo.find(player, taskId);
        if (progressOpt.isEmpty()) {
            log.error("Task progress not found for player " + player.getUniqueId() + " and task " + taskId);
        }
        return progressOpt;
    }

    private boolean executeProgressUpdate(Player player, String taskId, Function<TaskProgress, UpdateResult> calculation, BiConsumer<UUID, String> eventAction) {
        return safeExecute("updating task progress", () -> {
            Optional<TaskProgress> progressOpt = getTaskProgress(player, taskId);
            if (progressOpt.isEmpty()) return false;

            TaskProgress currentProgress = progressOpt.get();
            UpdateResult result = calculation.apply(currentProgress);

            updateAndSaveProgress(currentProgress, result.objectives, result.status);

            if (eventAction != null) {
                eventAction.accept(player.getUniqueId(), taskId);
            }
            return true;
        });
    }

    private UpdateResult calculateNewState(TaskProgress progress, Function<TaskObjective, TaskObjective> objectiveMapper) {
        List<TaskObjective> newObjectives = new ArrayList<>();
        boolean allFinished = true;
        
        for (TaskObjective obj : progress.getTaskDefinition().getObjectives()) {
            TaskObjective newObj = objectiveMapper.apply(obj);
            if (!newObj.isFinished()) allFinished = false;
            newObjectives.add(newObj);
        }
        
        return new UpdateResult(newObjectives, allFinished ? PTXTaskStatus.COMPLETED : PTXTaskStatus.IN_PROGRESS);
    }

    private List<TaskObjective> mapObjectives(TaskProgress progress, Function<TaskObjective, TaskObjective> mapper) {
        List<TaskObjective> list = new ArrayList<>();
        for (TaskObjective obj : progress.getTaskDefinition().getObjectives()) {
            list.add(mapper.apply(obj));
        }
        return list;
    }

    private TaskObjective createObjective(TaskObjective original, int amount, boolean finished) {
        return new TaskObjective(original.getAction(), original.getTarget(), finished, amount, original.getTargetAmount());
    }

    private void updateAndSaveProgress(TaskProgress oldProgress, List<TaskObjective> newObjectives, PTXTaskStatus newStatus) {
        TaskDefinition oldDef = oldProgress.getTaskDefinition();
        TaskDefinition newDef = new TaskDefinition(oldDef.getId(), oldDef.getType(), oldDef.getName(), oldDef.getDescription(), newObjectives);
        TaskProgress newProgress = new TaskProgress(oldProgress.getUuid(), newDef, newStatus, oldProgress.getCreateAt(), TimeUtil.getTime());
        progressRepo.save(newProgress);
    }

    // TODO
    private record UpdateResult(List<TaskObjective> objectives, PTXTaskStatus status) {}

    // --- Event Handling Stub ---

    public void registerEventListener(TaskEventListener listener) {
        // Implementation pending
    }

    public void unregisterEventListener(TaskEventListener listener) {
        // Implementation pending
    }

    public void reload() {
        // Implementation pending
    }

    private void fireTaskStartEvent(UUID playerId, String taskId) {
        // Implementation pending
    }

    private void fireTaskProgressEvent(UUID playerId, String taskId, int oldProgress, int newProgress, int targetProgress) {
        // Implementation pending
    }

    private void fireTaskCompleteEvent(UUID playerId, String taskId, long completionTime) {
        // Implementation pending
    }

    public void fireTaskFailEvent(UUID playerId, String taskId, String reason) {
        // Implementation pending
    }
}
