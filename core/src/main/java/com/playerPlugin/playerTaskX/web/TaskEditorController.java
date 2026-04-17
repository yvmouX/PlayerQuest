package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import io.javalin.http.Context;

import java.util.Collection;

public class TaskEditorController {
    private final TaskManager taskManager;

    public TaskEditorController(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void getAllTasks(Context ctx) {
        Collection<TaskDefinition> tasks = taskManager.getAllTasks();
        ctx.json(tasks);
    }

    public void createTask(Context ctx) {
        try {
            TaskDefinition task = ctx.bodyAsClass(TaskDefinition.class);
            ctx.status(201).json(task);
        } catch (Exception e) {
            ctx.status(400).result("Invalid task data");
        }
    }

    public void updateTask(Context ctx) {
        String id = ctx.pathParam("id");
        try {
            TaskDefinition task = ctx.bodyAsClass(TaskDefinition.class);
            ctx.json(task);
        } catch (Exception e) {
            ctx.status(400).result("Invalid task data");
        }
    }

    public void deleteTask(Context ctx) {
        String id = ctx.pathParam("id");
        ctx.status(204);
    }
}
