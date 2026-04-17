package com.playerPlugin.playerTaskX.listener;

import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class EntityListener implements Listener {
    private final TaskManager taskManager;

    public EntityListener(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        taskManager.handleEvent(killer, event);
    }
}
