package com.playerPlugin.playerTaskX.EventHandlers.handlers;

import com.playerPlugin.playerTaskX.structs.eventStructs.DeathEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class PlayerKillHandler implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public DeathEvent onPlayerKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return null;
        Player killer = event.getEntity().getKiller();
        EntityType victim = event.getEntityType();
        return new DeathEvent(killer, victim);
    }
}
