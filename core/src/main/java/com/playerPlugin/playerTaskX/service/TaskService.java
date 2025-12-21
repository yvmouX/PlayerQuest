package com.playerPlugin.playerTaskX.service;

import cn.yvmou.ylib.api.services.LoggerService;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.model.TaskDefinition;
import com.playerPlugin.playerTaskX.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.storage.TaskRepository;
import org.bukkit.entity.Player;

public class TaskService implements TaskAPI {
    private final LoggerService log;
    private final TaskRepository taskRepo;
    private final TaskProgressRepository progressRepo;
    private final TaskCache cache;

    public TaskService(LoggerService log, TaskRepository taskRepo, TaskProgressRepository progressRepo, TaskCache cache) {
        this.log = log;
        this.taskRepo = taskRepo;
        this.progressRepo = progressRepo;
        this.cache = cache;
    }

    public boolean createProgress(Player player, String taskId) {
        // 验证 ID 是否存在
        // Verify that the ID exists
        if (taskRepo.findById(taskId).isEmpty()) {
            log.error(String.format("Task with ID %s not found", taskId));
            return false;
        };
        TaskDefinition taskDef = taskRepo.findById(taskId).get();

        progressRepo.createForPlayer(player, taskDef);
        return true;
    }

    public void deleteProgress(Player player, String taskId) {
        // TODO
    }

    public void saveProgress(Player player, String taskId) {
        // TODO
    }

    public boolean createTask(TaskDefinition taskDef) {
        // 验证 Def 是否有效
        // Verify that the Def is valid
        if (taskDef.getId() == null || taskDef.getId().isEmpty()) {
            log.error("taskDefinition id required");
            return false;
        }
        // 验证仓库是否存在该 Def
        // verify that it exists in the repository
        if (taskRepo.findById(taskDef.getId()).isPresent()) {
            log.error(String.format("Task with ID %s already exists", taskDef.getId()));
            return false;
        }

        // 保存到仓库
        taskRepo.save(taskDef);
        // 保存到缓存
        cache.taskDefToCache(taskDef);
        return true;
    }
}
