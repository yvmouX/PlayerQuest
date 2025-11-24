package com.playerPlugin.playerTaskX.service;

import cn.yvmou.ylib.tools.LoggerTools;
import com.paperPlugin.api.TaskAPI;
import com.playerPlugin.playerTaskX.domain.Task.TaskDefinition;
import com.playerPlugin.playerTaskX.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.storage.TaskRepository;
import org.bukkit.entity.Player;

public class TaskService implements TaskAPI {
    private final LoggerTools log;
    private final TaskRepository taskRepo;
    private final TaskProgressRepository progressRepo;

    public TaskService(LoggerTools log, TaskRepository taskRepo, TaskProgressRepository progressRepo) {
        this.log = log;
        this.taskRepo = taskRepo;
        this.progressRepo = progressRepo;
    }

    public void createProgress(Player player, String taskId) {
        // 验证 ID 是否存在
        // Verify that the ID exists
        if (taskRepo.findById(taskId).isEmpty()) {
            log.error(String.format("Task with ID %s not found", taskId));
            return;
        };
        TaskDefinition taskDef = taskRepo.findById(taskId).get();
        progressRepo.createForPlayer(player, taskDef);
    }

    public void createTask(TaskDefinition taskDef) {
        // 验证 Def 是否有效
        // Verify that the Def is valid
        if (taskDef.getId() == null || taskDef.getId().isEmpty()) {
            log.error("taskDefinition id required");
            return;
        }
        // 验证仓库是否存在该 Def
        // verify that it exists in the repository
        if (taskRepo.findById(taskDef.getId()).isPresent()) {
            log.error(String.format("Task with ID %s already exists", taskDef.getId()));
            return;
        }
        taskRepo.save(taskDef);
    }
}
