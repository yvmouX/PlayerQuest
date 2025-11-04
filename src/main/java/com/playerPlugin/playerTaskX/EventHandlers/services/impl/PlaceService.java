package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

public class PlaceService implements EventCallback<BlockPlaceEvent> {
    @Override
    public void onEvent(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String placedBlockType = event.getBlockPlaced().getType().name();

    }

    private void checkTaskProgress() {

    }
}
