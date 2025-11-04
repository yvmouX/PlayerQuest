package com.playerPlugin.playerTaskX.EventHandlers.handlers.task;

import com.playerPlugin.playerTaskX.structs.eventStructs.BreakEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class PlayerBreakEvent implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public BreakEvent onPlayerBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return null;
        return new BreakEvent(
                event.getPlayer(), event.getBlock(),
                event.getBlock().getType(),
                event.getBlock().getLocation()
        );
    }
}
