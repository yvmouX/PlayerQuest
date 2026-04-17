package com.playerPlugin.playerTaskX.api.model.objective;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KillMobObjective implements Objective {
    private final String id;
    private final EntityType mobType;
    private final int amount;
    private final Map<UUID, Integer> progress = new ConcurrentHashMap<>();

    public KillMobObjective(String id, EntityType mobType, int amount) {
        this.id = id;
        this.mobType = mobType;
        this.amount = amount;
    }

    @Override
    public String getId() { return id; }
    @Override
    public int getTargetAmount() { return amount; }

    @Override
    public boolean matchesEvent(Event event) {
        if (event instanceof EntityDeathEvent e) {
            return e.getEntity().getType() == mobType
                && e.getEntity().getKiller() != null;
        }
        return false;
    }

    @Override
    public void applyProgress(Player player, int amount) {
        progress.merge(player.getUniqueId(), amount, Integer::sum);
    }

    @Override
    public boolean isCompleted(Player player) {
        return progress.getOrDefault(player.getUniqueId(), 0) >= amount;
    }
}
