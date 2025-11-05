package com.playerPlugin.playerTaskX.EventHandlers.handlers.task;

import com.playerPlugin.playerTaskX.structs.eventStructs.CraftEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;

public class CraftEventHandler implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public CraftEvent onCraftItem(CraftItemEvent event) {
        return null;
    }
}
