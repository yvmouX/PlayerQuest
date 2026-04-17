package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.manager.TaskManager;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;

public class EditorServer {
    private final TaskEditorController taskController;
    private final RewardTemplateController rewardController;
    private final PlayerProgressController progressController;
    private final StatsController statsController;
    private Javalin javalin;

    public EditorServer(TaskManager taskManager) {
        this.taskController = new TaskEditorController(taskManager);
        this.rewardController = new RewardTemplateController(taskManager);
        this.progressController = new PlayerProgressController(taskManager);
        this.statsController = new StatsController(taskManager);
    }

    public void start(int port) {
        this.javalin = Javalin.create(config -> {
            config.staticFiles.enableDirectoryBrowsing = false;
            config.cors = ctx -> ctx.allowHost("localhost", "127.0.0.1");
        })
            // 任务管理
            .get("/api/quests", ctx -> taskController.getAll(ctx))
            .get("/api/quests/:id", ctx -> taskController.getById(ctx))
            .post("/api/quests", ctx -> taskController.create(ctx))
            .put("/api/quests/:id", ctx -> taskController.update(ctx))
            .delete("/api/quests/:id", ctx -> taskController.delete(ctx))
            // 奖励模板
            .get("/api/rewards/templates", ctx -> rewardController.getAll(ctx))
            .post("/api/rewards/templates", ctx -> rewardController.save(ctx))
            .delete("/api/rewards/templates/:id", ctx -> rewardController.delete(ctx))
            // 玩家进度
            .get("/api/players/progress", ctx -> progressController.getAll(ctx))
            // 统计
            .get("/api/stats/completion", ctx -> statsController.getCompletion(ctx))
            .get("/api/stats/activity", ctx -> statsController.getActivity(ctx))
            .start("127.0.0.1", port);
    }

    public void stop() {
        if (javalin != null) {
            javalin.stop();
        }
    }
}
