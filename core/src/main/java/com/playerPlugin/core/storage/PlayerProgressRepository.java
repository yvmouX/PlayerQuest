package com.playerPlugin.core.storage;

import com.playerPlugin.core.domain.Task.TaskProgress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerProgressRepository {
    Optional<TaskProgress> find(UUID player, String taskId);

    List<TaskProgress> findByPlayer(UUID player);

    void save(TaskProgress progress);

    void delete(UUID player, String taskId);
}
