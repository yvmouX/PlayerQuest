package com.playerPlugin.infra.coreEvent;

import java.util.UUID;

public class CoreKillEvent {
    public final UUID player;
    public final String entityType;

    public CoreKillEvent(UUID player, String entityType) {
        this.player = player;
        this.entityType = entityType;
    }
}
