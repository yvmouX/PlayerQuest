package com.playerPlugin.playerTaskX.core.listener;

import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.core.engine.ApplyResult;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * 实体相关动作：击杀、垂钓、附魔、剪切、繁殖、与实体交互。
 */
public final class EntityListener extends ProgressListener implements Listener {

    public EntityListener(ProgressService progress, Consumer<ApplyResult> onProgress) {
        super(progress, onProgress);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        push(ProgressContext.of(killer, Trigger.KILL, event.getEntityType().name()));
    }

    /** 玩家被击杀时也算对方的击杀进度。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.equals(event.getEntity())) {
            return;
        }
        push(ProgressContext.of(killer, Trigger.KILL, "PLAYER"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        String target = null;
        if (event.getCaught() instanceof org.bukkit.entity.Item item) {
            ItemStack stack = item.getItemStack();
            target = stack.getType().name();
        }
        push(ProgressContext.of(event.getPlayer(), Trigger.FISH, target));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        // 一次附魔可能附加多个魔咒，取第一个作为目标判定依据，数量按 1 计
        String enchantment = event.getEnchantsToAdd().keySet().stream()
                .findFirst()
                .map(key -> key.getKey().getKey())
                .orElse(null);
        push(new ProgressContext(event.getEnchanter(), Trigger.ENCHANT, enchantment, 1, event.getItem().getType().name()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        push(ProgressContext.of(event.getPlayer(), Trigger.SHEAR, event.getEntity().getType().name()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) {
            return;
        }
        push(ProgressContext.of(player, Trigger.BREED, event.getEntity().getType().name()));
    }

    /**
     * 驯服。
     * <p>
     * {@link EntityTameEvent} 的「驯服者」是动物的主人（{@code getOwner()}），
     * 且事件在驯服成功后才触发，因此不需要额外判断成功率。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) {
            return;
        }
        push(ProgressContext.of(player, Trigger.TAME, event.getEntity().getType().name()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        push(new ProgressContext(event.getPlayer(), Trigger.INTERACT,
                event.getRightClicked().getType().name(), 1, "RIGHT_CLICK_ENTITY"));
    }
}
