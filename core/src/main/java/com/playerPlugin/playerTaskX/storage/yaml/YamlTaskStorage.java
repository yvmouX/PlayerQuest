package com.playerPlugin.playerTaskX.storage.yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class YamlTaskStorage implements TaskStorage {
    private final File dataFolder;
    private final ObjectMapper mapper;

    public YamlTaskStorage(File dataFolder) {
        this.dataFolder = new File(dataFolder, "tasks");
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.dataFolder.mkdirs();
    }

    @Override
    public void save(TaskDefinition task) {
        File file = new File(dataFolder, task.getId() + ".yml");
        try {
            mapper.writeValue(file, task);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save task: " + task.getId(), e);
        }
    }

    @Override
    public Optional<TaskDefinition> findById(String id) {
        File file = new File(dataFolder, id + ".yml");
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
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
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
        new File(dataFolder, id + ".yml").delete();
    }
}