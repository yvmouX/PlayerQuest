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

        // TODO: 实现真正的玩家进度查询
        // 目前返回模拟数据
        List<Map<String, Object>> mockData = new ArrayList<>();
        
        Map<String, Object> result = new HashMap<>();
        result.put("total", mockData.size());
        result.put("data", mockData);
        
        ctx.json(ApiResponse.success(result));
    }
}
