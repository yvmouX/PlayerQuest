package com.playerPlugin.playerTaskX.structs.eventStructs;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public record TameEvent(
        Player tamer,
        LivingEntity tamed
) {
}
