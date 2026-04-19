package com.playerPlugin.playerTaskX.storage.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JsonProgressStorage implements ProgressStorage {
    private final File dataFolder;
    private final ObjectMapper mapper;

    public JsonProgressStorage(File dataFolder) {
        this.dataFolder = new File(dataFolder, "playerdata");
        this.mapper = new ObjectMapper();
        this.dataFolder.mkdirs();
    }

    private File getPlayerFolder(UUID playerId) {
        return new File(dataFolder, playerId.toString());
    }

    @Override
    public void create(UUID playerId, TaskDefinition taskDefinition) {
        File file = new File(getPlayerFolder(playerId), taskDefinition.getId() + ".json");
        if (file.exists()) {
            throw new IllegalStateException("Progress already exists for player: " + playerId + " task: " + taskDefinition.getId());
        }
        TaskProgress progress = new TaskProgress(playerId, taskDefinition.getId());
        progress.setStatus(PTXTaskStatus.IN_PROGRESS);
        save(playerId, progress);
    }

    @Override
    public void update(UUID playerId, TaskProgress progress) {
        File file = new File(getPlayerFolder(playerId), progress.getTaskId() + ".json");
        if (!file.exists()) {
            throw new IllegalStateException("Progress does not exist for player: " + playerId + " task: " + progress.getTaskId());
        }
        save(playerId, progress);
    }

    @Override
    public void save(UUID playerId, TaskProgress progress) {
        File playerFolder = getPlayerFolder(playerId);
        playerFolder.mkdirs();
        File file = new File(playerFolder, progress.getTaskId() + ".json");
        try {
            mapper.writeValue(file, progress);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save progress for player: " + playerId, e);
        }
    }

    @Override
    public Optional<TaskProgress> findByPlayerAndTask(UUID playerId, String taskId) {
        File file = new File(getPlayerFolder(playerId), taskId + ".json");
        if (!file.exists()) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(file, TaskProgress.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TaskProgress> findByPlayer(UUID playerId) {
        List<TaskProgress> progressList = new ArrayList<>();
        File playerFolder = getPlayerFolder(playerId);
        if (!playerFolder.exists()) return progressList;
        File[] files = playerFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return progressList;
        for (File file : files) {
            try {
                progressList.add(mapper.readValue(file, TaskProgress.class));
            } catch (IOException e) {
                // Skip invalid files
            }
        }
        return progressList;
    }

    @Override
    public void delete(UUID playerId, String taskId) {
        new File(getPlayerFolder(playerId), taskId + ".json").delete();
    }
}