package com.playerPlugin.playerTaskX.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class YamlProgressStorage implements ProgressStorage {
    private final File dataFolder;
    private final ObjectMapper mapper;

    public YamlProgressStorage(File dataFolder) {
        this.dataFolder = new File(dataFolder, "playerdata");
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.dataFolder.mkdirs();
    }

    private File getPlayerFolder(UUID playerId) {
        return new File(dataFolder, playerId.toString());
    }

    @Override
    public void save(UUID playerId, TaskProgress progress) {
        File playerFolder = getPlayerFolder(playerId);
        playerFolder.mkdirs();
        File file = new File(playerFolder, progress.getTaskId() + ".yml");
        try {
            mapper.writeValue(file, progress);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save progress for player: " + playerId, e);
        }
    }

    @Override
    public Optional<TaskProgress> findByPlayerAndTask(UUID playerId, String taskId) {
        File file = new File(getPlayerFolder(playerId), taskId + ".yml");
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
        File[] files = playerFolder.listFiles((dir, name) -> name.endsWith(".yml"));
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
        new File(getPlayerFolder(playerId), taskId + ".yml").delete();
    }
}