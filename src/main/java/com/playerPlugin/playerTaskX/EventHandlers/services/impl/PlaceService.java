package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.Task.Task;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.TaskTarget;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.PlayerTask.Trigger.TaskTriggerExecutor;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlaceService implements EventCallback<BlockPlaceEvent> {
    @Override
    public void onEvent(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String placedBlockType = event.getBlockPlaced().getType().name();

    }
}
