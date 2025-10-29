package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import com.playerPlugin.playerTaskX.PlayerTask.Task.Task;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.structs.eventStructs.BreakEvent;

import java.util.List;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;


public class BreakService implements EventCallback<BreakEvent> {
    
    @Override
    public void onEvent(BreakEvent event) {
        // 处理统计数据收集逻辑
        String playerName = event.player().getName();
        String blockType = event.material().name();
        
        log.debug("统计数据更新: 玩家 " + playerName + " 破坏了 " + blockType);

        collectStatistics(event);
    }
    
    /**
     * 收集统计数据
     * @param event 破坏事件
     */
    private void collectStatistics(BreakEvent event) {
        // TODO: 实现具体的统计数据收集逻辑
        log.debug("正在收集玩家 " + event.player().getName() + " 的统计数据...");

        List<Task> pTasks = TaskManager.getInstance().verifyTaskTargetActions(event.player().getUniqueId(), "BREAK");

    }
}
