package com.playerPlugin.playerTaskX.storage;

import com.playerPlugin.playerTaskX.api.model.TaskDefinition;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    /**
     * Load all custom tasks from the repository.
     *
     * <p>
     *     YAML & JSON:
     *       Each task file is a YAML/JSON file, named after the task ID.
     *     SQLite: TODO
     *     MySQL: TODO
     * </p>
     *
     * @return {@link List }<{@link TaskDefinition }>
     */
    List<TaskDefinition> loadAll();

    /**
     * Find a task by its ID.
     *
     * <p>
     *     If it does not exist, returns Optional.empty().
     * </p>
     *
     * @param id Task ID
     * @return {@link Optional }<{@link TaskDefinition }>
     */
    Optional<TaskDefinition> findById(String id);

    /**
     * Save task to the repository.
     *
     * @param taskDefinition TaskDefinition
     */
    void save(TaskDefinition taskDefinition);

    /**
     * Delete task from the repository.
     *
     * @param id Task ID
     */
    void delete(String id);
}
