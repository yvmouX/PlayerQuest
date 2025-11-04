package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXTaskStatus;
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
    }

}
