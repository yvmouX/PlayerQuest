package com.playerPlugin.playerTaskX.structs.eventStructs;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

@Deprecated(forRemoval = true)
public record TameEvent(
        Player tamer,
        LivingEntity tamed
) {
}
