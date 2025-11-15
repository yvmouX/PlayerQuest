package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import com.playerPlugin.playerTaskX.domain.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import static com.playerPlugin.playerTaskX.utils.Help.log;

public class CraftService implements EventCallback<CraftItemEvent> {
    private final TaskManager tm;
    private final StorgeManager sm;

    public CraftService(TaskManager tm, StorgeManager sm) {
        this.tm = tm;
        this.sm = sm;
    }

    @Override
    public void onEvent(CraftItemEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack craftedItem = event.getCurrentItem();

        log.info("Crafted item: " + craftedItem);
    }
}
