package com.playerPlugin.playerTaskX.api.model.objective;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractObjective implements Objective {
    protected final String id;
    protected final int amount;
    protected final Map<UUID, Integer> progress = new ConcurrentHashMap<>();

    protected AbstractObjective(String id, int amount) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.id = id;
        this.amount = amount;
    }

    @Override
    public String getId() { return id; }

    @Override
    public int getTargetAmount() { return amount; }

    @Override
    public void applyProgress(Player player, int delta) {
        if (delta <= 0) return;
        progress.merge(player.getUniqueId(), delta, Integer::sum);
    }

    @Override
    public boolean isCompleted(Player player) {
        return progress.getOrDefault(player.getUniqueId(), 0) >= amount;
    }

    protected int getProgress(UUID playerId) {
        return progress.getOrDefault(playerId, 0);
    }
}
