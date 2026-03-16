package com.playerPlugin.playerTaskX.event.listeners;

import com.playerPlugin.playerTaskX.api.Enum.PTXActionType;
import com.playerPlugin.playerTaskX.event.TaskRouter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class InteractListener implements Listener {
    private final TaskRouter router;

    public InteractListener(TaskRouter router) {
        this.router = router;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        router.dispatch(e.getPlayer(), PTXActionType.DROP, e.getItemDrop().getItemStack().getType().name(), e.getItemDrop().getItemStack().getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player) {
            Player player = (Player) e.getEntity();
            router.dispatch(player, PTXActionType.TAKE, e.getItem().getItemStack().getType().name(), e.getItem().getItemStack().getAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR) // Interact event cancellation might not matter for trigger? Let's respect it.
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null) {
            router.dispatch(e.getPlayer(), PTXActionType.TRIGGER, e.getClickedBlock().getType().name(), 1);
        } else if (e.getItem() != null) {
            router.dispatch(e.getPlayer(), PTXActionType.TRIGGER, e.getItem().getType().name(), 1);
        }
    }
}