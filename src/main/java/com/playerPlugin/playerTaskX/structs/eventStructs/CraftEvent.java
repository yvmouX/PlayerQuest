package com.playerPlugin.playerTaskX.structs.eventStructs;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Deprecated(forRemoval = true)
public record CraftEvent(
        Player player,
        ItemStack item
) {
}
