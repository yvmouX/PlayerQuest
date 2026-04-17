package com.playerPlugin.playerTaskX.event;

import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinHandler implements Listener {
    private final TaskManager taskManager;

    public PlayerJoinHandler(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        taskManager.loadPlayerProgress(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
    }
}
