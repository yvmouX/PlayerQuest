package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import com.playerPlugin.playerTaskX.domain.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;

import static com.playerPlugin.playerTaskX.utils.Help.log;

public class FishingService implements EventCallback<PlayerFishEvent> {
    private final TaskManager tm;
    private final StorgeManager sm;

    public FishingService(TaskManager tm, StorgeManager sm) {
        this.tm = tm;
        this.sm = sm;
    }

    @Override
    public void onEvent(PlayerFishEvent event) {
        Player player = event.getPlayer();
        Entity caught = event.getCaught();

        log.info("Player " + player.getName() + " fished " + caught);
    }
}
