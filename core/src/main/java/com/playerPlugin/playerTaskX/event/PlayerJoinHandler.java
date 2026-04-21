package com.playerPlugin.playerTaskX.event;

import com.playerPlugin.playerTaskX.engine.QuestEngine;
import com.playerPlugin.playerTaskX.engine.QuestSessionManager;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;

public class PlayerJoinHandler implements Listener {
    private final TaskManager taskManager;
    private final QuestEngine questEngine;

    public PlayerJoinHandler(TaskManager taskManager, QuestEngine questEngine) {
        this.taskManager = taskManager;
        this.questEngine = questEngine;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        log.debug("Player Join Event");
        Player player = event.getPlayer();
        taskManager.loadPlayerProgress(player.getUniqueId());
        questEngine.restorePlayerSession(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
    }
}
