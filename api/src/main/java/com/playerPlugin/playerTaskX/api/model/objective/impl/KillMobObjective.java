package com.playerPlugin.playerTaskX.api.model.objective.impl;

import com.playerPlugin.playerTaskX.api.model.objective.AbstractObjective;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;

public class KillMobObjective extends AbstractObjective {
    private final EntityType mobType;

    public KillMobObjective(String id, EntityType mobType, int amount) {
        super(id, amount);
        this.mobType = mobType;
    }

    @Override
    public boolean matchesEvent(Event event) {
        if (event instanceof EntityDeathEvent e) {
            return e.getEntity().getType() == mobType
                && e.getEntity().getKiller() != null;
        }
        return false;
    }
}
