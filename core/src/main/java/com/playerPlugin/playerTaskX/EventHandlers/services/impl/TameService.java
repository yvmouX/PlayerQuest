package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import com.playerPlugin.playerTaskX.domain.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTameEvent;

import static com.playerPlugin.playerTaskX.utils.Help.log;

public class TameService implements EventCallback<EntityTameEvent> {
    private final TaskManager tm;
    private final StorgeManager sm;

    public TameService(TaskManager tm, StorgeManager sm) {
        this.tm = tm;
        this.sm = sm;
    }

    @Override
    public void onEvent(EntityTameEvent event) {
        Player player = (Player) event.getOwner();
        Entity tamed = event.getEntity();

        log.info("Tamed entity: " + tamed.getName());
    }
}
