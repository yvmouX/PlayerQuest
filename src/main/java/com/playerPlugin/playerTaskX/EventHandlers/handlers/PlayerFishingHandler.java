package com.playerPlugin.playerTaskX.EventHandlers.handlers;

import com.playerPlugin.playerTaskX.structs.eventStructs.FishingEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.List;

public class PlayerFishingHandler implements Listener {
    @EventHandler
    public FishingEvent onPlayerFishing(PlayerFishEvent event) {
        return new FishingEvent(event.getPlayer(), event.getCaught(), event.getState());
    }
}
