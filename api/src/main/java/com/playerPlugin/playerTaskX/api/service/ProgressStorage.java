package com.playerPlugin.playerTaskX.api.service;

import com.playerPlugin.playerTaskX.api.model.TaskProgress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgressStorage {
    void save(UUID playerId, TaskProgress progress);
    Optional<TaskProgress> findByPlayerAndTask(UUID playerId, String taskId);
    List<TaskProgress> findByPlayer(UUID playerId);
    void delete(UUID playerId, String taskId);
}
