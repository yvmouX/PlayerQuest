package com.playerPlugin.playerTaskX.event.listeners;

import com.playerPlugin.playerTaskX.api.Enum.PTXActionType;
import com.playerPlugin.playerTaskX.event.TaskRouter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

public class EntityListener implements Listener {
    private final TaskRouter router;

    public EntityListener(TaskRouter router) {
        this.router = router;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player) {
            Player player = (Player) e.getBreeder();
            router.dispatch(player, PTXActionType.BREED, e.getEntity().getType().name(), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (e.getOwner() instanceof Player) {
            Player player = (Player) e.getOwner();
            router.dispatch(player, PTXActionType.TAME, e.getEntity().getType().name(), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) {
        router.dispatch(e.getPlayer(), PTXActionType.SCISSOR, e.getEntity().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH && e.getCaught() != null) {
            router.dispatch(e.getPlayer(), PTXActionType.FISHING, e.getCaught().getType().name(), 1);
            // Ideally check item type if it's an item, but entity type is safer for now (e.g. ITEM)
            // If caught is an item, we might want item type.
            if (e.getCaught() instanceof org.bukkit.entity.Item) {
                 router.dispatch(e.getPlayer(), PTXActionType.FISHING, ((org.bukkit.entity.Item)e.getCaught()).getItemStack().getType().name(), 1);
            }
        }
    }
}