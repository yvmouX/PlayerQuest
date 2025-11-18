package com.playerPlugin.core.EventHandlers.services.impl;

import com.playerPlugin.core.EventHandlers.services.EventCallback;
import com.playerPlugin.core.domain.PlayerTask.TaskManager;
import com.playerPlugin.core.dataManager.StorgeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.ItemStack;

import static com.playerPlugin.core.utils.Help.log;

public class FoodLevelService implements EventCallback<FoodLevelChangeEvent> {
    private final TaskManager tm;
    private final StorgeManager sm;

    public FoodLevelService(TaskManager tm, StorgeManager sm) {
        this.tm = tm;
        this.sm = sm;
    }

    @Override
    public void onEvent(FoodLevelChangeEvent event) {
        Player player = (Player) event.getEntity();
        ItemStack ateItem = event.getItem();

        log.info("Player ate item: " + ateItem);
    }
}
