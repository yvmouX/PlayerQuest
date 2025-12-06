package com.playerPlugin.playerTaskX.service;

import com.playerPlugin.playerTaskX.model.Task.TaskDefinition;
import org.bukkit.entity.Player;

public interface TaskAPI {
    /**
     * 创建任务进度
     * @param player 玩家
     * @param taskId 任务ID
     */
    boolean createProgress(Player player, String taskId);

    /**
     * 创建任务
     * @param taskDef 任务定义
     */
    boolean createTask(TaskDefinition taskDef);
}
