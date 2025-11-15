package com.playerPlugin.playerTaskX.structs.eventStructs;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;

@Deprecated(forRemoval = true)
public record FishingEvent(
    Player player,
    Entity caught, // null, Entity
    PlayerFishEvent.State state // BITE, CAUGHT_ENTITY, CAUGHT_FISH, FAILED_ATTEMPT, FISHING, IN_GROUND, REEL_IN
) {
}
