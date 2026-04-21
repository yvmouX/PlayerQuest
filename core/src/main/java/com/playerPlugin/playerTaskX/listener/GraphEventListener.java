package com.playerPlugin.playerTaskX.listener;

import com.playerPlugin.playerTaskX.engine.QuestEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class GraphEventListener implements Listener {
    private final QuestEngine questEngine;
    
    public GraphEventListener(QuestEngine questEngine) {
        this.questEngine = questEngine;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        questEngine.updateProgress(player, event);
        questEngine.handleEvent(player, event);
    }
}