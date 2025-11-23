package com.playerPlugin.playerTaskX.utils;

import java.util.UUID;

public final class CoreEventUtil {
    public static String typeOf(Object coreEvent) {
        if (coreEvent instanceof CoreKillEvent) return "kill";
        if (coreEvent instanceof CoreCraftEvent) return "craft";
        // ... fallback to event.getClass().getSimpleName()
        return coreEvent.getClass().getSimpleName().toLowerCase();
    }

    public static UUID playerOf(Object coreEvent) {
        if (coreEvent instanceof CoreKillEvent) return ((CoreKillEvent) coreEvent).player;
        if (coreEvent instanceof CoreCraftEvent) return ((CoreCraftEvent) coreEvent).player;
        // ...
        throw new IllegalArgumentException("unsupported event");
    }
}
