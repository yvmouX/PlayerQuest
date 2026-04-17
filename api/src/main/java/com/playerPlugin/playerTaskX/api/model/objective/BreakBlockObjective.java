package com.playerPlugin.playerTaskX.api.model.objective;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BreakBlockObjective implements Objective {
    private final String id;
    private final Material blockType;
    private final int amount;
    private final Map<UUID, Integer> progress = new ConcurrentHashMap<>();

    public BreakBlockObjective(String id, Material blockType, int amount) {
        this.id = id;
        this.blockType = blockType;
        this.amount = amount;
    }

    @Override
    public String getId() { return id; }
    @Override
    public int getTargetAmount() { return amount; }

    @Override
    public boolean matchesEvent(Event event) {
        if (event instanceof BlockBreakEvent e) {
            return e.getBlock().getType() == blockType;
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
