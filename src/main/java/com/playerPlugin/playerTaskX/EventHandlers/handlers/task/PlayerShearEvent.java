package com.playerPlugin.playerTaskX.EventHandlers.handlers.task;

import com.playerPlugin.playerTaskX.structs.eventStructs.ShearEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerShearEntityEvent;

public class PlayerShearEvent implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public ShearEvent onPlayerShear(PlayerShearEntityEvent event) {
        if (event.isCancelled()) return null;

        return new ShearEvent(
                event.getPlayer(),
                event.getEntity(),
                event.getItem()
        );
    }
}
