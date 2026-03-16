package com.playerPlugin.playerTaskX.event.listeners;

import com.playerPlugin.playerTaskX.api.Enum.PTXActionType;
import com.playerPlugin.playerTaskX.event.TaskRouter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockListener implements Listener {
    private final TaskRouter router;

    public BlockListener(TaskRouter router) {
        this.router = router;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        router.dispatch(e.getPlayer(), PTXActionType.BREAK, e.getBlock().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        router.dispatch(e.getPlayer(), PTXActionType.PLACE, e.getBlock().getType().name(), 1);
    }
}