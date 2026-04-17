package com.playerPlugin.playerTaskX.api.model.objective;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public interface Objective {
    String getId();
    int getTargetAmount();
    boolean matchesEvent(Event event);
    void applyProgress(Player player, int amount);
    boolean isCompleted(Player player);
}
