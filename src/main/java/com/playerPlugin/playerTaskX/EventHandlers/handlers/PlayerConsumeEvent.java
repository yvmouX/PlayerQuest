package com.playerPlugin.playerTaskX.EventHandlers.handlers;

import com.playerPlugin.playerTaskX.structs.eventStructs.ConsumeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

public class PlayerConsumeEvent implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public ConsumeEvent onPlayerEat(FoodLevelChangeEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player player)) return null;
        return new ConsumeEvent(
                player,
                event.getItem()
        );
    }
}
