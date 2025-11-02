package com.playerPlugin.playerTaskX.structs.eventStructs;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public record ShearEvent(
        Player player,
        Entity shearedEntity,
        ItemStack usedItem
) {
}
