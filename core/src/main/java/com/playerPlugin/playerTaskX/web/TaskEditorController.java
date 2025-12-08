package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.model.Task.TaskDefinition;
import com.playerPlugin.playerTaskX.storage.TaskRepository;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.openapi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class TaskEditorController {
    private final TaskRepository taskRepo;

    public TaskEditorController(TaskRepository taskRepo) {
        this.taskRepo = taskRepo;
    }

    @OpenApi(
        path = "/api/taskDefList",
        methods = HttpMethod.GET,
        operationId = "getTaskDefList",
        summary = "获取任务列表",
        description = "获取系统中所有任务定义的列表",
        tags = { "Task" },
        responses = {
            @OpenApiResponse(
                status = "200",
                description = "成功获取任务列表",
                content = @OpenApiContent(
                    from = TaskDefinition[].class,
                    mimeType = "application/json"
                )
            ),
            @OpenApiResponse(
                status = "500",
                description = "服务器内部错误"
            )
        }
    )
    public void getTaskDefList(@NotNull Context ctx) {
        List<TaskDefinition> taskDefList = taskRepo.loadAll();
        ctx.json(Map.of(
                "data", taskDefList
        ));
    }
}
