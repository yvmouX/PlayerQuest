package com.playerPlugin.core.repository;

import com.playerPlugin.core.domain.PlayerTask.Task.PlayerTask;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerProgressRepository {
    Optional<PlayerTask> find(UUID player, String taskId);

    List<PlayerTask> findByPlayer(UUID player);

    void save(PlayerTask progress);

    void delete(UUID player, String taskId);
}
