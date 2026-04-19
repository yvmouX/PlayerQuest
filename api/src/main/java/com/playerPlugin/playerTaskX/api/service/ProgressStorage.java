package com.playerPlugin.playerTaskX.api.service;

import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgressStorage {

    /**
     * Create task progress.
     *
     * @param playerId Player ID
     * @param taskDefinition Task definition
     */
    void create(UUID playerId, TaskDefinition taskDefinition);

    /**
     * Find task progress for a player.
     *
     * @param playerId Player ID
     * @param taskId Task ID
     * @return {@link Optional }<{@link TaskProgress }>
     */
    Optional<TaskProgress> findByPlayerAndTask(UUID playerId, String taskId);

    /**
     * Find all task progress for a player.
     *
     * @param playerId Player ID
     * @return {@link List }<{@link TaskProgress }>
     */
    List<TaskProgress> findByPlayer(UUID playerId);

    /**
     * Update task progress.
     *
     * @param playerId Player ID
     * @param progress Task progress
     */
    void update(UUID playerId, TaskProgress progress);

    /**
     * Save (upsert) task progress.
     *
     * @param playerId Player ID
     * @param progress Task progress
     */
    void save(UUID playerId, TaskProgress progress);

    /**
     * Delete task progress.
     *
     * @param playerId Player ID
     * @param taskId Task ID
     */
    void delete(UUID playerId, String taskId);
}
