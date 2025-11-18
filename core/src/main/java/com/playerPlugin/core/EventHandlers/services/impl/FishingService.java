package com.playerPlugin.core.EventHandlers.services.impl;

import com.playerPlugin.core.EventHandlers.services.EventCallback;
import com.playerPlugin.core.domain.PlayerTask.TaskManager;
import com.playerPlugin.core.dataManager.StorgeManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;

import static com.playerPlugin.core.utils.Help.log;

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
