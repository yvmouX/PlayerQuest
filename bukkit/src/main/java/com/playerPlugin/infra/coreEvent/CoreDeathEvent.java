package com.playerPlugin.infra.coreEvent;

import java.util.UUID;

public class CoreDeathEvent {
    public final UUID player;
    public final String deathType;

    public CoreDeathEvent(UUID player, String deathType) {
        this.player = player;
        this.deathType = deathType;
    }
}
