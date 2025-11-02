package com.playerPlugin.playerTaskX.EventHandlers.handlers;

import com.playerPlugin.playerTaskX.structs.eventStructs.PlaceEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class PlayerPlaceEvent implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public PlaceEvent onPlayerPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return null;
        return new PlaceEvent(
                event.getPlayer(),
                event.getBlock()
        );
    }
}
