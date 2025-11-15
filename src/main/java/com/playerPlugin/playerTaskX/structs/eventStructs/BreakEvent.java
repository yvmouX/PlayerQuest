package com.playerPlugin.playerTaskX.structs.eventStructs;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

@Deprecated(forRemoval = true)
public record BreakEvent(
        Player player,
        Block block,
        Material material,
        Location blockLocation
) {
}
