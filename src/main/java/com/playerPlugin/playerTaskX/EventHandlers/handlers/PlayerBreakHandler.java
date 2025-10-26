package com.playerPlugin.playerTaskX.EventHandlers.handlers;

import com.playerPlugin.playerTaskX.structs.eventStructs.BreakEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class PlayerBreakHandler implements Listener {
    @EventHandler
    public BreakEvent onPlayerBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        return new BreakEvent(player, event.getBlock(), event.getBlock().getType(), event.getBlock().getLocation());
    }
}
