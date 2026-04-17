package com.playerPlugin.playerTaskX.listener;

import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class BlockListener implements Listener {
    private final TaskManager taskManager;

    public BlockListener(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        taskManager.handleEvent(player, event);
    }
}
