package com.playerPlugin.core.EventHandlers.services.impl;

import com.playerPlugin.core.EventHandlers.services.EventCallback;
import com.playerPlugin.core.domain.PlayerTask.TaskManager;
import com.playerPlugin.core.dataManager.StorgeManager;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerShearEntityEvent;

import static com.playerPlugin.core.utils.Help.log;

public class ShearService implements EventCallback<PlayerShearEntityEvent> {
    private final TaskManager tm;
    private final StorgeManager sm;

    public ShearService(TaskManager tm, StorgeManager sm) {
        this.tm = tm;
        this.sm = sm;
    }

    @Override
    public void onEvent(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        EntityType shearedEntityType = event.getEntity().getType();

        log.info("Sheared entity type: " + shearedEntityType);
    }
}
