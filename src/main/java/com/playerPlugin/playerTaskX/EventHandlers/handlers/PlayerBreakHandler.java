package com.playerPlugin.playerTaskX.EventHandlers.handlers;

import com.playerPlugin.playerTaskX.services.EventCallbackManager;
import com.playerPlugin.playerTaskX.structs.eventStructs.BreakEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class PlayerBreakHandler implements Listener {
    private final EventCallbackManager callbackManager = EventCallbackManager.getInstance();
    
    @EventHandler
    public void onPlayerBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        BreakEvent breakEvent = new BreakEvent(player, event.getBlock(), 
                                              event.getBlock().getType(), 
                                              event.getBlock().getLocation());
        
        // 触发所有注册的回调
        callbackManager.triggerCallbacks(breakEvent);
    }
}
