package com.playerPlugin.infra.adapter;

import com.playerPlugin.infra.coreEvent.CoreKillEvent;
import com.playerPlugin.core.event.EventBus;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class EntityKillListener implements Listener {
    private final EventBus bus;

    public EntityKillListener(EventBus bus) {
        this.bus = bus;
    }

    @EventHandler
    public void onEntityKill(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (!(e.getDamager() instanceof Player)) return;
        bus.post(
                new CoreKillEvent(
                        ((Player) e.getDamager()).getUniqueId(),
                        e.getEntity().getType().toString()
                )
        );
    }
}
