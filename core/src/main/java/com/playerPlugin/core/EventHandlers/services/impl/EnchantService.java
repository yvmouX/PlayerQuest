package com.playerPlugin.core.EventHandlers.services.impl;

import com.playerPlugin.core.EventHandlers.services.EventCallback;
import com.playerPlugin.core.domain.PlayerTask.TaskManager;
import com.playerPlugin.core.dataManager.StorgeManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.enchantment.EnchantItemEvent;

import java.util.Map;

import static com.playerPlugin.core.utils.Help.log;

public class EnchantService implements EventCallback<EnchantItemEvent> {
    private final TaskManager tm;
    private final StorgeManager sm;

    public EnchantService(TaskManager tm, StorgeManager sm) {
        this.tm = tm;
        this.sm = sm;
    }

    @Override
    public void onEvent(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        Map<Enchantment, Integer> enchantment = event.getEnchantsToAdd();

        log.info("Enchanting item: " + event.getItem().getType() + " with enchantment: " + enchantment);
    }
}
