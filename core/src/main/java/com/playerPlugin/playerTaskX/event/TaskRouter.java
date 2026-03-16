package com.playerPlugin.playerTaskX.event;

import cn.yvmou.ylib.api.logger.Logger;
import com.playerPlugin.playerTaskX.TaskAPI;
import com.playerPlugin.playerTaskX.api.Enum.PTXActionType;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.model.ObjectiveDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class TaskRouter {
    private final TaskAPI api;
    private final Logger log;

    public TaskRouter(TaskAPI api, Logger log) {
        this.api = api;
        this.log = log;
    }

    public void dispatch(Player player, PTXActionType action, String target, int amount) {
        if (player == null) return;

        // 获取玩家所有正在进行的任务
        List<TaskProgress> activeTasks = api.getPlayerTasks(player);

        for (TaskProgress progress : activeTasks) {
            if (progress.getStatus() != PTXTaskStatus.IN_PROGRESS) continue;

            // 获取当前任务定义
            Optional<TaskDefinition> taskDefOpt = api.getTaskDefinition(progress.getTaskId());
            if (taskDefOpt.isEmpty()) continue;
            TaskDefinition taskDef = taskDefOpt.get();

            // 遍历任务的所有目标
            for (String objId : taskDef.getObjectives()) {
                Optional<ObjectiveDefinition> objDefOpt = api.getObjectiveDefinition(objId);
                if (objDefOpt.isEmpty()) continue;
                ObjectiveDefinition objDef = objDefOpt.get();

                // 检查目标条件是否匹配
                log.debug("Checking objective " + objId + " for action " + action + " and target " + target);
                if (matches(objDef, action, target)) {
                    log.to(player).debug("Objective " + objId + " matched for action " + action + " and target " + target);
                    // 为该任务的当前目标增加进度
                    api.incrementTaskProgress(player.getUniqueId(), taskDef.getId(), objId, amount);
                }
            }
        }
    }

    private boolean matches(ObjectiveDefinition obj, PTXActionType action, String target) {
        if (obj.getAction() != action) return false;
        
        // 不区分大小写比较
        return obj.getTarget().equalsIgnoreCase(target);
    }
}
