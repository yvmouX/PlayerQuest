package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.ItemStack;

import static com.playerPlugin.playerTaskX.utils.Help.log;

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
