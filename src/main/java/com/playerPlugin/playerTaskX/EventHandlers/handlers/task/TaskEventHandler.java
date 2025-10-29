package com.playerPlugin.playerTaskX.EventHandlers.handlers.task;

import com.playerPlugin.playerTaskX.EventHandlers.EventCallbackManager;
import com.playerPlugin.playerTaskX.EventHandlers.EventsRegister;
import com.playerPlugin.playerTaskX.structs.eventStructs.BreakEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class TaskEventHandler implements Listener {
    EventCallbackManager cbm = EventsRegister.getCallbackManager();

    @EventHandler
    public void onPlayerBreak(BlockBreakEvent event) {
        cbm.triggerCallbacks(new BreakEvent(
                event.getPlayer(), event.getBlock(),
                event.getBlock().getType(),
                event.getBlock().getLocation()
        ));
    }
    
    @EventHandler
    @SuppressWarnings("deprecation")
    public void onPlayerPlace(BlockPlaceEvent e) {
        cbm.triggerCallbacks(new BlockPlaceEvent(
                e.getBlockPlaced(),
                e.getBlockReplacedState(),
                e.getBlockAgainst(),
                e.getItemInHand(),
                e.getPlayer(),
                e.canBuild()
        ));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        cbm.triggerCallbacks(new PlayerDeathEvent(
                e.getEntity(),
                e.getDamageSource(),
                e.getDrops(),
                e.getDroppedExp(),
                e.getDeathMessage()
        ));
    }
    // TODO


}
