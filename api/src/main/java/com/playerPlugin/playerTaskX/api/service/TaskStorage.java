package com.playerPlugin.playerTaskX.api.service;

import com.playerPlugin.playerTaskX.api.model.TaskDefinition;

import java.util.List;
import java.util.Optional;

public interface TaskStorage {
    void save(TaskDefinition task);
    Optional<TaskDefinition> findById(String id);
    List<TaskDefinition> findAll();
    void delete(String id);
}
