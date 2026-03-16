package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.api.logger.Logger;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.event.TaskEventListener;
import com.playerPlugin.playerTaskX.api.model.ObjectiveDefinition;
import com.playerPlugin.playerTaskX.api.model.RewardDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.api.storage.ObjectiveRepository;
import com.playerPlugin.playerTaskX.api.storage.RewardRepository;
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
    private final ObjectiveRepository objectiveRepo;
    private final RewardRepository rewardRepo;
    private final TaskProgressRepository progressRepo;
    private final TaskCache cache;

    public TaskAPI(Logger log, StorageFactory storageFactory, TaskCache cache) {
        this.log = log;
        this.cache = cache;

        this.taskRepo = storageFactory.getRepository();
        this.objectiveRepo = storageFactory.getObjectiveRepository();
        this.rewardRepo = storageFactory.getRewardRepository();
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

    // --- Objective Definition Management ---

    public boolean createObjective(ObjectiveDefinition objDef) {
        return safeExecute("creating objective", () -> {
            if (isInvalidId(objDef.getId())) return false;
            
            if (objectiveRepo.findById(objDef.getId()).isPresent()) {
                log.error("Objective with ID " + objDef.getId() + " already exists");
                return false;
            }

            objectiveRepo.save(objDef);
            cache.addObjectiveDef(objDef);
            log.info("Objective with ID " + objDef.getId() + " created successfully");
            return true;
        });
    }

    public boolean deleteObjective(String objID) {
        return safeExecute("deleting objective", () -> {
            if (isInvalidId(objID)) return false;

            ObjectiveDefinition objDef = objectiveRepo.findById(objID).orElse(null);
            if (objDef == null) {
                log.error("Objective with ID " + objID + " does not exist");
                return false;
            }

            objectiveRepo.delete(objID);
            cache.removeObjectiveDef(objDef);
            log.info("Objective with ID " + objDef.getId() + " deleted successfully");
            return true;
        });
    }

    public Optional<ObjectiveDefinition> getObjectiveDefinition(String objId) {
        return cache.getObjectiveDef(objId);
    }

    // --- Reward Definition Management ---

    public boolean createReward(RewardDefinition rewardDef) {
        return safeExecute("creating reward", () -> {
            if (isInvalidId(rewardDef.getId())) return false;
            
            if (rewardRepo.findById(rewardDef.getId()).isPresent()) {
                log.error("Reward with ID " + rewardDef.getId() + " already exists");
                return false;
            }

            rewardRepo.save(rewardDef);
            cache.addRewardDef(rewardDef);
            log.info("Reward with ID " + rewardDef.getId() + " created successfully");
            return true;
        });
    }

    public boolean deleteReward(String rewardID) {
        return safeExecute("deleting reward", () -> {
            if (isInvalidId(rewardID)) return false;

            RewardDefinition rewardDef = rewardRepo.findById(rewardID).orElse(null);
            if (rewardDef == null) {
                log.error("Reward with ID " + rewardID + " does not exist");
                return false;
            }

            rewardRepo.delete(rewardID);
            cache.removeRewardDef(rewardDef);
            log.info("Reward with ID " + rewardDef.getId() + " deleted successfully");
            return true;
        });
    }

    public Optional<RewardDefinition> getRewardDefinition(String rewardId) {
        return cache.getRewardDef(rewardId);
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
            TaskDefinition def = cache.getTaskDef(taskId).orElse(null);
            if (def == null) return new UpdateResult(PTXTaskStatus.IN_PROGRESS);
            
            boolean allFinished = true;
            
            for (int i = 0; i < def.getObjectives().size(); i++) {
                String objId = def.getObjectives().get(i);
                ObjectiveDefinition obj = cache.getObjectiveDef(objId).orElse(null);
                if (obj == null) continue;
                
                int target = obj.getTargetAmount();
                int newAmount = Math.min(progress, target); // Assuming same progress for all? Might need revision later
                
                currentProgress.setObjectiveAmount(objId, newAmount);
                if (newAmount < target) allFinished = false;
            }
            return new UpdateResult(allFinished ? PTXTaskStatus.COMPLETED : PTXTaskStatus.IN_PROGRESS);
        }, null);
    }

    public boolean incrementTaskProgress(UUID playerId, String taskId, String objectiveId, int amount) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;

        return executeProgressUpdate(player, taskId, (currentProgress) -> {
            TaskDefinition def = cache.getTaskDef(taskId).orElse(null);
            if (def == null) return new UpdateResult(PTXTaskStatus.IN_PROGRESS);
            
            boolean allFinished = true;
            boolean objectiveFound = false;
            
            for (String objId : def.getObjectives()) {
                ObjectiveDefinition obj = cache.getObjectiveDef(objId).orElse(null);
                if (obj == null) continue;
                
                int target = obj.getTargetAmount();
                int currentAmount = currentProgress.getObjectiveAmount(objId);
                
                // Only update the requested objective
                if (objId.equals(objectiveId)) {
                    objectiveFound = true;
                    int newAmount = Math.min(currentAmount + amount, target);
                    currentProgress.setObjectiveAmount(objId, newAmount);
                    if (newAmount < target) allFinished = false;
                } else {
                    // Check other objectives status
                    if (currentAmount < target) allFinished = false;
                }
            }
            
            if (!objectiveFound) {
                // If the objective ID wasn't found in the task definition, we shouldn't change status
                return new UpdateResult(currentProgress.getStatus());
            }
            
            return new UpdateResult(allFinished ? PTXTaskStatus.COMPLETED : PTXTaskStatus.IN_PROGRESS);
        }, null);
    }

    public boolean completeTask(UUID playerId, String taskId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;

        return executeProgressUpdate(player, taskId, (currentProgress) -> {
            TaskDefinition def = cache.getTaskDef(taskId).orElse(null);
            if (def != null) {
                for (int i = 0; i < def.getObjectives().size(); i++) {
                    String objId = def.getObjectives().get(i);
                    ObjectiveDefinition obj = cache.getObjectiveDef(objId).orElse(null);
                    if (obj != null) {
                        currentProgress.setObjectiveAmount(objId, obj.getTargetAmount());
                    }
                }
            }
            return new UpdateResult(PTXTaskStatus.COMPLETED);
        }, (uuid, id) -> fireTaskCompleteEvent(uuid, id, System.currentTimeMillis()));
    }

    public boolean resetTask(UUID playerId, String taskId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;

        return executeProgressUpdate(player, taskId, (currentProgress) -> {
            currentProgress.getObjectiveProgress().clear();
            return new UpdateResult(PTXTaskStatus.IN_PROGRESS);
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
    
    public Optional<TaskDefinition> getTaskDefinition(String taskId) {
        return cache.getTaskDef(taskId);
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
            return Optional.empty();
        }
        return progressOpt;
    }

    private boolean executeProgressUpdate(Player player, String taskId, Function<TaskProgress, UpdateResult> calculation, BiConsumer<UUID, String> eventAction) {
        return safeExecute("updating task progress", () -> {
            Optional<TaskProgress> progressOpt = getTaskProgress(player, taskId);
            if (progressOpt.isEmpty()) return false;

            TaskProgress currentProgress = progressOpt.get();
            UpdateResult result = calculation.apply(currentProgress);

            currentProgress.setStatus(result.status);
            progressRepo.save(currentProgress);

            if (eventAction != null) {
                eventAction.accept(player.getUniqueId(), taskId);
            }
            return true;
        });
    }

    private record UpdateResult(PTXTaskStatus status) {}

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
