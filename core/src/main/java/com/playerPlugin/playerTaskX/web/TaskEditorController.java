package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import io.javalin.http.Context;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class TaskEditorController {
    private final TaskManager taskManager;

    public TaskEditorController(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    private void validateGraph(QuestGraph graph) throws IllegalArgumentException {
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            throw new IllegalArgumentException("Graph must have at least one node");
        }

        long startCount = graph.getNodes().stream()
                .filter(n -> "start".equals(n.getNodeType()))
                .count();
        if (startCount > 1) {
            throw new IllegalArgumentException("Graph can only have one start node, found " + startCount);
        }

        long taskCount = graph.getNodes().stream()
                .filter(n -> "task".equals(n.getNodeType()))
                .count();
        if (taskCount > 1) {
            throw new IllegalArgumentException("Graph can only have one task node, found " + taskCount);
        }

        long completionCount = graph.getNodes().stream()
                .filter(n -> "completion".equals(n.getNodeType()))
                .count();
        if (completionCount > 1) {
            throw new IllegalArgumentException("Graph can only have one completion node, found " + completionCount);
        }
    }

    public void getAll(Context ctx) {
        Collection<TaskDefinition> tasks = taskManager.getAllTasks();
        ctx.json(ApiResponse.success(tasks));
    }

    public void getById(Context ctx) {
        String id = ctx.pathParam("id");
        TaskDefinition task = taskManager.getTask(id).orElse(null);
        if (task == null) {
            ctx.status(404).json(ApiResponse.error(404, "Task not found"));
            return;
        }
        ctx.json(ApiResponse.success(task));
    }

    public void create(Context ctx) {
        try {
            TaskDefinition task = ctx.bodyAsClass(TaskDefinition.class);
            if (task.hasGraph()) {
                validateGraph(task.getGraph());
            }
            taskManager.saveTask(task);
            ctx.status(201).json(ApiResponse.success(task));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(400, "Invalid task data: " + e.getMessage()));
        } catch (Exception e) {
            ctx.status(400).json(ApiResponse.error(400, "Invalid task data: " + e.getMessage()));
        }
    }

    public void update(Context ctx) {
        String id = ctx.pathParam("id");
        try {
            TaskDefinition task = ctx.bodyAsClass(TaskDefinition.class);
            if (task.hasGraph()) {
                validateGraph(task.getGraph());
            }
            taskManager.saveTask(task);
            ctx.json(ApiResponse.success(task));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(400, "Invalid task data: " + e.getMessage()));
        } catch (Exception e) {
            ctx.status(400).json(ApiResponse.error(400, "Invalid task data: " + e.getMessage()));
        }
    }

    public void delete(Context ctx) {
        String id = ctx.pathParam("id");
        taskManager.deleteTask(id);
        ctx.json(ApiResponse.success(null));
    }

    public void batchCreate(Context ctx) {
        try {
            @SuppressWarnings("unchecked")
            List<TaskDefinition> tasks = ctx.bodyAsClass(List.class);
            int count = 0;
            for (Object obj : tasks) {
                if (obj instanceof TaskDefinition) {
                    TaskDefinition task = (TaskDefinition) obj;
                    if (task.hasGraph()) {
                        validateGraph(task.getGraph());
                    }
                    taskManager.saveTask(task);
                    count++;
                }
            }
            ctx.status(201).json(ApiResponse.success(Map.of("count", count)));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(400, "Invalid task data: " + e.getMessage()));
        } catch (Exception e) {
            ctx.status(400).json(ApiResponse.error(400, "Invalid task data: " + e.getMessage()));
        }
    }
}
