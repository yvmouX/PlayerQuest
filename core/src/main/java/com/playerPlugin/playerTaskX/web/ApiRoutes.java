package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.model.Task.TaskDefinition;
import com.playerPlugin.playerTaskX.storage.TaskRepository;
import com.playerPlugin.playerTaskX.web.exception.NotFoundTaskDefinitionException;
import io.javalin.Javalin;

import java.util.Map;

public class ApiRoutes {
    public void setupApiRoutes(Javalin app, TaskRepository taskRepo) {
        // ***************** GET *****************
        // 获取任务列表
        app.get("/api/taskDefList", new TaskEditorController(taskRepo)::getTaskDefList);

        // 获取单个任务
        app.get("/api/taskDef/{id}", ctx -> {
            String questId = ctx.pathParam("id");
            TaskDefinition taskDef = taskRepo.findById(questId).orElse(null);

            if (taskDef == null) throw new NotFoundTaskDefinitionException();

            ctx.json(Map.of("data", taskDef));
        });
    }
}
