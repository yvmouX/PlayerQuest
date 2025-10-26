package com.playerPlugin.playerTaskX.structs.eventStructs;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public record DeathEvent(
        Player killer,
        EntityType victim
){ }
