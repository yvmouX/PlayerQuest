package com.playerPlugin.playerTaskX.api;

import com.playerPlugin.playerTaskX.api.event.TaskEventListener;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface TaskAPI {

    /**
     * Create task definition
     * 
     * @param taskDef
     * @return
     */
    boolean createTask(TaskDefinition taskDef);

    /**
     * Delete task definition
     * 
     * @param taskId
     * @return
     */
    boolean deleteTask(String taskId);

    /**
     * Create progress for player
     * 
     * @param player
     * @param taskId
     * @return
     */
    boolean createProgress(Player player, String taskId);

    /**
     * Delete progress for player
     * 
     * @param player
     * @param taskId
     */
    boolean deleteProgress(Player player, String taskId);

    /**
     * Update progress for player
     * 
     * @param player
     * @param taskId
     * @return
     */
    boolean updateProgress(Player player, String taskId, int progress);

    /**
     * Increment progress for player
     * 
     * @param playerId
     * @param taskId
     * @param amount
     * @return
     */
    boolean incrementTaskProgress(UUID playerId, String taskId, int amount);

    /**
     * Complete task for player
     * 
     * @param playerId
     * @param taskId
     * @return
     */
    boolean completeTask(UUID playerId, String taskId);

    /**
     * Reset task progress for player
     * 
     * @param playerId
     * @param taskId
     * @return
     */
    boolean resetTask(UUID playerId, String taskId);

    /**
     * Check if player has completed task
     * 
     * @param playerId
     * @param taskId
     * @return
     */
    boolean isTaskCompleted(UUID playerId, String taskId);

    /**
     * Register event listener
     * 
     * @param listener
     */
    void registerEventListener(TaskEventListener listener);

    /**
     * Unregister event listener
     * 
     * @param listener
     */
    void unregisterEventListener(TaskEventListener listener);

    /**
     * Reload plugin configuration
     */
    void reload();
}
