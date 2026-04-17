package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.manager.TaskManager;
import io.javalin.Javalin;
import io.javalin.http.Context;

public class EditorServer {
    private final TaskEditorController controller;
    private Javalin javalin;

    public EditorServer(TaskManager taskManager) {
        this.controller = new TaskEditorController(taskManager);
    }

    public void start(int port) {
        this.javalin = Javalin.create()
            .get("/api/tasks", ctx -> controller.getAllTasks(ctx))
            .post("/api/tasks", ctx -> controller.createTask(ctx))
            .put("/api/tasks/{id}", ctx -> controller.updateTask(ctx))
            .delete("/api/tasks/{id}", ctx -> controller.deleteTask(ctx))
            .start(port);
    }

    public void stop() {
        if (javalin != null) {
            javalin.stop();
        }
    }
}
