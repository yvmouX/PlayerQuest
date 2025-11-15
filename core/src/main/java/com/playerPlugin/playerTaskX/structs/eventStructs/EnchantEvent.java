package com.playerPlugin.playerTaskX.structs.eventStructs;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

@Deprecated(forRemoval = true)
public record EnchantEvent(
        Player player,
        Map<Enchantment, Integer> enchantments,
        ItemStack enchantedItem
) {
}
