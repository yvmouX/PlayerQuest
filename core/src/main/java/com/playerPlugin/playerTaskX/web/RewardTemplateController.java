package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.api.model.reward.Reward;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RewardTemplateController {
    private final Map<String, Reward> templateStore = new ConcurrentHashMap<>();
    private final TaskManager taskManager;

    public RewardTemplateController(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void getAll(Context ctx) {
        ctx.json(ApiResponse.success(templateStore.values()));
    }

    public void save(Context ctx) {
        try {
            Reward reward = ctx.bodyAsClass(Reward.class);
            if (reward.getId() == null || reward.getId().isEmpty()) {
                reward.setId(java.util.UUID.randomUUID().toString());
            }
            templateStore.put(reward.getId(), reward);
            ctx.status(201).json(ApiResponse.success(reward));
        } catch (Exception e) {
            ctx.status(400).json(ApiResponse.error(400, "Invalid reward data: " + e.getMessage()));
        }
    }

    public void delete(Context ctx) {
        String id = ctx.pathParam("id");
        Reward removed = templateStore.remove(id);
        if (removed == null) {
            ctx.status(404).json(ApiResponse.error(404, "Template not found"));
            return;
        }
        ctx.json(ApiResponse.success(null));
    }
}