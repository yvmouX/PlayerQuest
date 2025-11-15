package com.playerPlugin.playerTaskX.EventHandlers.handlers.task;

import com.playerPlugin.playerTaskX.EventHandlers.EventCallbackManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

public class TaskEventHandler implements Listener {
    EventCallbackManager cbm = EventCallbackManager.getInstance();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;
        cbm.triggerCallbacks(new BlockBreakEvent(
                e.getBlock(),
                e.getPlayer()
        ));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPlace(BlockPlaceEvent e) {
        if (e.isCancelled()) return;
        cbm.triggerCallbacks(new BlockPlaceEvent(
                e.getBlockPlaced(),
                e.getBlockReplacedState(),
                e.getBlockAgainst(),
                e.getItemInHand(),
                e.getPlayer(),
                e.canBuild(),
                e.getHand()
        ));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(EntityDeathEvent e) {
        cbm.triggerCallbacks(new EntityDeathEvent(
                e.getEntity(),
                e.getDamageSource(),
                e.getDrops(),
                e.getDroppedExp()
        ));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerEnchant(EnchantItemEvent e) {
        if (e.isCancelled()) return;
        cbm.triggerCallbacks(new EnchantItemEvent(
                e.getEnchanter(),
                e.getView(),
                e.getEnchantBlock(),
                e.getItem(),
                e.getExpLevelCost(),
                e.getEnchantsToAdd(),
                e.getEnchantmentHint(),
                e.getLevelHint(),
                e.whichButton()
        ));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerFishing(PlayerFishEvent e) {
        if (e.isCancelled()) return;
        cbm.triggerCallbacks(new PlayerFishEvent(
                e.getPlayer(),
                e.getCaught(),
                e.getHook(),
                e.getHand(),
                e.getState()
        ));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCraft(CraftItemEvent e) {
        if (e.isCancelled()) return;
        cbm.triggerCallbacks(new CraftItemEvent(
                e.getRecipe(),
                e.getView(),
                e.getSlotType(),
                e.getSlot(),
                e.getClick(),
                e.getAction(),
                e.getHotbarButton()
        ));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerFoolLevelChange(FoodLevelChangeEvent e) {
        if (e.isCancelled()) return;
        cbm.triggerCallbacks(new FoodLevelChangeEvent(
                e.getEntity(),
                e.getFoodLevel(),
                e.getItem()
        ));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerShear(PlayerShearEntityEvent e) {
        if (e.isCancelled()) return;
        cbm.triggerCallbacks(new PlayerShearEntityEvent(
                e.getPlayer(),
                e.getEntity(),
                e.getItem(),
                e.getHand()
        ));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerTame(EntityTameEvent e) {
        if (e.isCancelled() || !(e.getOwner() instanceof Player)) return;
        cbm.triggerCallbacks(new EntityTameEvent(
                e.getEntity(),
                e.getOwner()
        ));
    }
}
