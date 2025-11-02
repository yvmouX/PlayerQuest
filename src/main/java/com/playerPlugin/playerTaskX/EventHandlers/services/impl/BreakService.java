package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import com.playerPlugin.playerTaskX.PlayerTask.Enum.PlayerTaskStatus;
import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.Task.Task;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.structs.eventStructs.BreakEvent;
import com.playerPlugin.playerTaskX.utils.TaskUtil;
import org.bukkit.entity.Player;

import java.util.List;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;


public class BreakService implements EventCallback<BreakEvent> {
    
    @Override
    public void onEvent(BreakEvent event) {
        // 处理统计数据收集逻辑
        Player player = event.player();
        String blockType = event.material().name();
        
        log.debug("统计数据更新: 玩家 " + player + " 破坏了 " + blockType);

        collectStatistics(player);
    }
    
    /**
     * 收集统计数据
     */
    private void collectStatistics(Player player) {
        List<PlayerTask> activeTasks = TaskManager.getInstance().getPlayerActiveTasks(player.getUniqueId());

        for (PlayerTask playerTask : activeTasks) {
            if (!playerTask.getStatus().equals(PlayerTaskStatus.IN_PROGRESS)) continue;
            if (!playerTask.getProgress())

           playerTask.getStatus();
            TaskUtil.getRequirePartMap(task.getTask().getId(), task.getTask().getTarget().getTarget_id(), task);
        }

        playerTasks.forEach(task -> {
            task.getTask().get
        })

        TaskUtil.getRequirePartMap()
        // TODO: 实现具体的统计数据收集逻辑
        log.debug("正在收集玩家 " + event.player().getName() + " 的统计数据...");

        List<Task> pTasks = TaskManager.getInstance().verifyTaskTargetActions(event.player().getUniqueId(), "BREAK");

    }
}
