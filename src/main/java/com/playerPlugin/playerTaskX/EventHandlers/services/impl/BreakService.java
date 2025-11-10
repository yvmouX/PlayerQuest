package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import com.playerPlugin.playerTaskX.structs.eventStructs.BreakEvent;
import org.bukkit.entity.Player;


public class BreakService implements EventCallback<BreakEvent> {
    
    @Override
    public void onEvent(BreakEvent event) {
        Player player = event.player();
        String blockType = event.material().name();
    }

}
