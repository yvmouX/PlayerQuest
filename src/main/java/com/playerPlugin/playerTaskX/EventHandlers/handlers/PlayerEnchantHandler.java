package com.playerPlugin.playerTaskX.EventHandlers.handlers;

import com.playerPlugin.playerTaskX.structs.eventStructs.EnchantEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;

public class PlayerEnchantHandler implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public EnchantEvent onPlayerEnchant(EnchantItemEvent event) {
        if (event.isCancelled()) return null;
        return new EnchantEvent(
                event.getEnchanter(),
                event.getEnchantsToAdd(),
                event.getItem()
        );
    }
}
