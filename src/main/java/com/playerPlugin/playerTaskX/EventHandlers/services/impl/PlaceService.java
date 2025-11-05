package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.List;
import java.util.Map;

public class PlaceService implements EventCallback<BlockPlaceEvent> {
    @Override
    public void onEvent(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material placedBlockType = event.getBlockPlaced().getType();

        checkTaskProgress(player, placedBlockType);
    }

    private void checkTaskProgress(Player player, Material placedBlockType) {
        // TODO
    }
}
