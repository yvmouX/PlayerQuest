package com.playerPlugin.playerTaskX.EventHandlers.handlers.task;

import com.playerPlugin.playerTaskX.structs.eventStructs.TameEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTameEvent;

public class PlayerTameEvent implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public TameEvent onPlayerTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player) || event.isCancelled()) return null;

        return new TameEvent(
                player,
                event.getEntity()
        );
    }
}
