package com.playerPlugin.playerTaskX.EventHandlers.handlers;

import com.playerPlugin.playerTaskX.structs.eventStructs.FishingEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

public class PlayerFishingHandler implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public FishingEvent onPlayerFishing(PlayerFishEvent event) {
        if (event.isCancelled()) return null;
        return new FishingEvent(event.getPlayer(), event.getCaught(), event.getState());
    }
}
