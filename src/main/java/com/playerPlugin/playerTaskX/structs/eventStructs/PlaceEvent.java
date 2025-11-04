package com.playerPlugin.playerTaskX.structs.eventStructs;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public record PlaceEvent(
        Player player,
        Block block
) {
}
