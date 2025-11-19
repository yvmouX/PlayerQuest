package com.playerPlugin.infra.storage;

import com.playerPlugin.core.domain.Task.TaskDefinition;
import com.playerPlugin.core.repository.TaskRepository;

import java.util.List;
import java.util.Optional;

public class JsonTaskRepository implements TaskRepository {
    @Override
    public List<TaskDefinition> loadAll() {
        return List.of();
    }

    @Override
    public Optional<TaskDefinition> findById(String id) {
        return Optional.empty();
    }

    @Override
    public void save(TaskDefinition taskDefinition) {

    }

    @Override
    public void delete(String id) {

    }
}
