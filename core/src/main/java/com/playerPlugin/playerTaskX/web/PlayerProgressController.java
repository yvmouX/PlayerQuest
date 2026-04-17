package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import io.javalin.http.Context;

import java.util.*;

public class PlayerProgressController {
    private final TaskManager taskManager;

    public PlayerProgressController(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void getAll(Context ctx) {
        String search = ctx.queryParam("search");
        String status = ctx.queryParam("status");
        
        int limit = 20;
        int offset = 0;
        try {
            String limitStr = ctx.queryParam("limit");
            if (limitStr != null) limit = Integer.parseInt(limitStr);
        } catch (NumberFormatException ignored) {}
        try {
            String offsetStr = ctx.queryParam("offset");
            if (offsetStr != null) offset = Integer.parseInt(offsetStr);
        } catch (NumberFormatException ignored) {}

        List<Map<String, Object>> allProgress = new ArrayList<>();
        for (TaskProgress progress : taskManager.getAllProgress()) {
            if (search != null && !progress.getPlayerUuid().toString().contains(search)) continue;
            if (status != null && !progress.getStatus().name().equalsIgnoreCase(status)) continue;
            
            Map<String, Object> item = new HashMap<>();
            item.put("playerUuid", progress.getPlayerUuid().toString());
            item.put("taskId", progress.getTaskId());
            item.put("status", progress.getStatus().name());
            allProgress.add(item);
        }

        int total = allProgress.size();
        int end = Math.min(offset + limit, allProgress.size());
        List<Map<String, Object>> page = offset < allProgress.size() 
            ? allProgress.subList(offset, end) 
            : Collections.emptyList();

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("data", page);
        
        ctx.json(ApiResponse.success(result));
    }
}
