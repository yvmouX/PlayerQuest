package com.playerPlugin.infra.adapter;

import com.playerPlugin.infra.coreEvent.CoreDeathEvent;
import com.playerPlugin.core.event.EventBus;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class EntityDeathListener implements Listener {
    private EventBus bus;

    public EntityDeathListener(EventBus bus) {
        this.bus = bus;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        bus.post(
                new CoreDeathEvent(
                        killer.getUniqueId(),
                        e.getEntity().getType().toString()
                )
        );
    }
}
