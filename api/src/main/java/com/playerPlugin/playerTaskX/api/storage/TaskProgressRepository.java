package com.playerPlugin.playerTaskX.api.storage;

import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskProgressRepository {

    /**
     * Create task progress
     *
     * @param player   Player
     * @param taskDefinition TaskDefinition
     */
    void create(Player player, TaskDefinition taskDefinition);

    /**
     * Find task progress for a player
     *
     * @param player Player
     * @param taskId Task ID
     * @return {@link Optional }<{@link TaskProgress }>
     */
    Optional<TaskProgress> find(Player player, String taskId);

    /**
     * Find task progress for a player
     *
     * @param player Player
     * @return {@link List }<{@link TaskProgress }>
     */
    List<TaskProgress> findAll(Player player);

    /**
     * Update task progress
     *
     * @param progress TaskProgress
     */
    void update(TaskProgress progress);

    /**
     * Save task progress
     *
     * @param progress TaskProgress
     */
    void save(TaskProgress progress);

    /**
     * Delete task progress
     *
     * @param player Player
     * @param taskId Task ID
     */
    void delete(UUID player, String taskId);
}
