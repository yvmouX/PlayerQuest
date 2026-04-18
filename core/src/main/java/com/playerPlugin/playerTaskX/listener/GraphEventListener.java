package com.playerPlugin.playerTaskX.listener;

import com.playerPlugin.playerTaskX.engine.QuestEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class GraphEventListener implements Listener {
    private final QuestEngine questEngine;
    
    public GraphEventListener(QuestEngine questEngine) {
        this.questEngine = questEngine;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            Bukkit.getScheduler().runTaskLater(null, () -> {
                questEngine.handleEvent(killer, event);
            }, 1L);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(null, () -> {
            questEngine.handleEvent(player, event);
        }, 1L);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(null, () -> {
            questEngine.handleEvent(player, event);
        }, 1L);
    }
}