package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.api.TaskAPI;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.storage.TaskRepository;
import com.playerPlugin.playerTaskX.exception.NotFoundTaskDefinitionException;
import io.javalin.Javalin;

import java.util.Map;

public class ApiRoutes {
    public void setupApiRoutes(Javalin app, TaskRepository taskRepo, TaskAPI taskAPI) {
        // ***************** GET *****************
        /*
          获取任务列表
         */
        app.get("/api/taskDefList", new TaskEditorController(taskRepo)::getTaskDefList);

        /*
          获取单个任务
         */
        app.get("/api/taskDef/{id}", ctx -> {
            String questId = ctx.pathParam("id");
            TaskDefinition taskDef = taskRepo.findById(questId).orElse(null);

            if (taskDef == null) throw new NotFoundTaskDefinitionException();

            ctx.json(Map.of("data", taskDef));
        });

        // ***************** POST *****************
        /*
          创建任务
         */
        app.post("/api/create", ctx -> {
            TaskDefinition taskDef = ctx.bodyAsClass(TaskDefinition.class);

            boolean success = taskAPI.createTask(taskDef);
            if (success) {
                ctx.status(201).json(Map.of(
                        "success", true,
                        "data", taskDef,
                        "message", "任务创建成功"
                ));
            } else {
                ctx.status(400).json(Map.of(
                        "success", false,
                        "message", "任务创建失败"
                ));
            }
        });
    }


}
