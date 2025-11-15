package com.playerPlugin.playerTaskX.structs.eventStructs;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

@Deprecated(forRemoval = true)
public record DeathEvent(
        Player killer,
        EntityType victim
){ }
