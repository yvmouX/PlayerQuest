package com.playerPlugin.playerTaskX.storage.json;

import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.storage.TaskRepository;

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
