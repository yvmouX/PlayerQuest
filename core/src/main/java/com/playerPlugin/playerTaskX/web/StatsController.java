package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import io.javalin.http.Context;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StatsController {

    private final TaskManager taskManager;

    public StatsController(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void getCompletion(Context ctx) {
        int notStarted = 0;
        int inProgress = 0;
        int completed = 0;

        for (Map<String, TaskProgress> playerProgress : taskManager.getPlayerProgressCache().values()) {
            for (TaskProgress progress : playerProgress.values()) {
                switch (progress.getStatus()) {
                    case NOT_STARTED -> notStarted++;
                    case IN_PROGRESS -> inProgress++;
                    case COMPLETED, CLAIMED -> completed++;
                    case ABANDONED -> {}
                }
            }
        }

        List<Map<String, Object>> data = new ArrayList<>();
        data.add(Map.of("name", "已完成", "value", completed));
        data.add(Map.of("name", "进行中", "value", inProgress));
        data.add(Map.of("name", "未开始", "value", notStarted));

        ctx.json(ApiResponse.success(data));
    }

    public void getActivity(Context ctx) {
        String range = ctx.queryParam("range");
        int days = (range != null && range.equals("30d")) ? 30 : 7;

        Map<String, Set<UUID>> dailyActivePlayers = new java.util.HashMap<>();
        for (int i = 0; i < days; i++) {
            String day = LocalDate.now().minusDays(i).toString();
            dailyActivePlayers.put(day, new HashSet<>());
        }

        for (TaskProgress progress : taskManager.getAllProgress()) {
            String day = Instant.ofEpochMilli(progress.getAcceptedAt())
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString();
            if (dailyActivePlayers.containsKey(day)) {
                dailyActivePlayers.get(day).add(progress.getPlayerId());
            }
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).toString();
            int users = dailyActivePlayers.get(day).size();
            data.add(Map.of("day", day, "users", users));
        }

        ctx.json(ApiResponse.success(data));
    }
}
