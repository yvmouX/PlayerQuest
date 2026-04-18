package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.manager.GraphManager;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import io.javalin.Javalin;

import java.nio.file.Path;

public class EditorServer {
    private final TaskEditorController taskController;
    private final RewardTemplateController rewardController;
    private final PlayerProgressController progressController;
    private final StatsController statsController;
    private final GraphStorageController graphController;
    private Javalin javalin;

    public EditorServer(TaskManager taskManager, Path dataFolder) {
        this.taskController = new TaskEditorController(taskManager);
        this.rewardController = new RewardTemplateController(taskManager, dataFolder);
        this.progressController = new PlayerProgressController(taskManager);
        this.statsController = new StatsController(taskManager);
        GraphManager graphManager = new GraphManager();
        this.graphController = new GraphStorageController(graphManager);
    }

    public void start(int port) {
        this.javalin = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.directory = "/web";
            });
        })
            // 任务管理
            .get("/api/quests", ctx -> taskController.getAll(ctx))
            .get("/api/quests/{id}", ctx -> taskController.getById(ctx))
            .post("/api/quests", ctx -> taskController.create(ctx))
            .post("/api/quests/batch", ctx -> taskController.batchCreate(ctx))
            .put("/api/quests/{id}", ctx -> taskController.update(ctx))
            .delete("/api/quests/{id}", ctx -> taskController.delete(ctx))
            // 图存储
            .get("/api/graphs", ctx -> graphController.getAll(ctx))
            .get("/api/graphs/{id}", ctx -> graphController.getById(ctx))
            .put("/api/graphs/{id}", ctx -> graphController.save(ctx))
            .delete("/api/graphs/{id}", ctx -> graphController.delete(ctx))
            // 奖励模板
            .get("/api/rewards/templates", ctx -> rewardController.getAll(ctx))
            .post("/api/rewards/templates", ctx -> rewardController.save(ctx))
            .delete("/api/rewards/templates/{id}", ctx -> rewardController.delete(ctx))
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
