package com.playerPlugin.playerTaskX.structs.eventStructs;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public record ConsumeEvent(
        Player player,
        ItemStack consumedItem
) {
}
