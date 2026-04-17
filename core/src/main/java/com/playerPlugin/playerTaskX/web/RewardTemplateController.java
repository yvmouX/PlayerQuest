package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.api.model.RewardDefinition;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import io.javalin.http.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RewardTemplateController {
    private final Map<String, RewardDefinition> templateStore = new ConcurrentHashMap<>();
    private final TaskManager taskManager;
    private final Path templateFile;
    private final ObjectMapper mapper;

    public RewardTemplateController(TaskManager taskManager, Path dataFolder) {
        this.taskManager = taskManager;
        this.templateFile = dataFolder.resolve("reward-templates.yml");
        this.mapper = new ObjectMapper(new YAMLFactory());
        loadTemplates();
    }

    private void loadTemplates() {
        if (Files.exists(templateFile)) {
            try {
                List<RewardDefinition> list = mapper.readValue(templateFile.toFile(),
                        mapper.getTypeFactory().constructCollectionType(List.class, RewardDefinition.class));
                for (RewardDefinition reward : list) {
                    if (reward.getId() != null) {
                        templateStore.put(reward.getId(), reward);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void saveTemplates() {
        try {
            List<RewardDefinition> list = new ArrayList<>(templateStore.values());
            mapper.writeValue(templateFile.toFile(), list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getAll(Context ctx) {
        ctx.json(ApiResponse.success(templateStore.values()));
    }

    public void save(Context ctx) {
        try {
            RewardDefinition reward = ctx.bodyAsClass(RewardDefinition.class);
            if (reward.getId() == null || reward.getId().isEmpty()) {
                // Can't set id on immutable RewardDefinition, create new instance
                String newId = java.util.UUID.randomUUID().toString();
                reward = new RewardDefinition(newId, reward.getType(), reward.getContent(), reward.getAmount());
            }
            templateStore.put(reward.getId(), reward);
            saveTemplates();
            ctx.status(201).json(ApiResponse.success(reward));
        } catch (Exception e) {
            ctx.status(400).json(ApiResponse.error(400, "Invalid reward data: " + e.getMessage()));
        }
    }

    public void delete(Context ctx) {
        String id = ctx.pathParam("id");
        RewardDefinition removed = templateStore.remove(id);
        if (removed == null) {
            ctx.status(404).json(ApiResponse.error(404, "Template not found"));
            return;
        }
        saveTemplates();
        ctx.json(ApiResponse.success(null));
    }
}