package com.playerPlugin.playerTaskX.event.listeners;

import com.playerPlugin.playerTaskX.api.Enum.PTXActionType;
import com.playerPlugin.playerTaskX.event.TaskRouter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

public class InventoryListener implements Listener {
    private final TaskRouter router;

    public InventoryListener(TaskRouter router) {
        this.router = router;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (e.getWhoClicked() instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) e.getWhoClicked();
            int amount = 1;
            if (e.isShiftClick()) {
                // Shift click logic is complex to calculate exact amount, defaulting to 1 or max stack logic if needed.
                // For simplicity, we count per action or 1.
                // To be precise, we should check the result item stack size * number of crafts.
                // But let's start with 1 per event or result amount.
                amount = e.getRecipe().getResult().getAmount();
                // This is not accurate for shift-click, but it's a start.
            } else {
                amount = e.getRecipe().getResult().getAmount();
            }
            router.dispatch(player, PTXActionType.CRAFT, e.getRecipe().getResult().getType().name(), amount);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        router.dispatch(e.getEnchanter(), PTXActionType.ENCHANT, e.getItem().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        router.dispatch(e.getPlayer(), PTXActionType.CONSUME, e.getItem().getType().name(), 1);
    }
}