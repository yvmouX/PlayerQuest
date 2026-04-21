package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.manager.TaskManager;
import com.playerPlugin.playerTaskX.web.controller.*;
import io.javalin.Javalin;

import java.nio.file.Path;

public class EditorServer {
    private final TaskEditorController taskController;
    private final ObjectiveTemplateController objectiveController;
    private final ActionTemplateController actionController;
    private final PlayerProgressController progressController;
    private final StatsController statsController;
    private Javalin javalin;

    public EditorServer(TaskManager taskManager, Path dataFolder) {
        this.taskController = new TaskEditorController(taskManager);
        this.objectiveController = new ObjectiveTemplateController(dataFolder);
        this.actionController = new ActionTemplateController(dataFolder);
        this.progressController = new PlayerProgressController(taskManager);
        this.statsController = new StatsController(taskManager);
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
            // 目标模板
            .get("/api/objectives/templates", ctx -> objectiveController.getAll(ctx))
            .get("/api/objectives/templates/{id}", ctx -> objectiveController.getById(ctx))
            .post("/api/objectives/templates", ctx -> objectiveController.save(ctx))
            .delete("/api/objectives/templates/{id}", ctx -> objectiveController.delete(ctx))
            // 行为模板
            .get("/api/actions/templates", ctx -> actionController.getAll(ctx))
            .get("/api/actions/templates/{id}", ctx -> actionController.getById(ctx))
            .post("/api/actions/templates", ctx -> actionController.save(ctx))
            .delete("/api/actions/templates/{id}", ctx -> actionController.delete(ctx))
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
