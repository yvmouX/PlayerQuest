package com.playerPlugin.playerTaskX.storage;

import com.playerPlugin.playerTaskX.domain.Task.TaskDefinition;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    List<TaskDefinition> loadAll();

    Optional<TaskDefinition> findById(String id);

    void save(TaskDefinition taskDefinition);

    void delete(String id);
}
