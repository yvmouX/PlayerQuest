package com.playerPlugin.playerTaskX.storage.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JsonTaskStorage implements TaskStorage {
    private final File dataFolder;
    private final ObjectMapper mapper;

    public JsonTaskStorage(File dataFolder) {
        this.dataFolder = new File(dataFolder, "tasks");
        this.mapper = new ObjectMapper();
        this.dataFolder.mkdirs();
    }

    @Override
    public void save(TaskDefinition task) {
        File file = new File(dataFolder, task.getId() + ".json");
        try {
            mapper.writeValue(file, task);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save task: " + task.getId(), e);
        }
    }

    @Override
    public Optional<TaskDefinition> findById(String id) {
        File file = new File(dataFolder, id + ".json");
        if (!file.exists()) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(file, TaskDefinition.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TaskDefinition> findAll() {
        List<TaskDefinition> tasks = new ArrayList<>();
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return tasks;
        for (File file : files) {
            try {
                tasks.add(mapper.readValue(file, TaskDefinition.class));
            } catch (IOException e) {
                // Skip invalid files
            }
        }
        return tasks;
    }

    @Override
    public void delete(String id) {
        new File(dataFolder, id + ".json").delete();
    }
}