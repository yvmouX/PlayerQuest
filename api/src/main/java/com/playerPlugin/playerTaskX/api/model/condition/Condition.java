package com.playerPlugin.playerTaskX.api.model.condition;

import org.bukkit.entity.Player;

public interface Condition {
    boolean isMet(Player player);
}
