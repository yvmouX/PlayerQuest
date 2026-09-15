package com.playerPlugin.playerTaskX.core.listener;

import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.core.engine.ApplyResult;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.integration.mythicmobs.MythicMobsHook;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** 实体相关动作：击杀、垂钓、剪切、繁殖、驯服、与实体交互。 */
public final class EntityListener extends ProgressListener implements Listener {

    /** MythicMobs 接入点；null 表示服务端没有（或不支持）MythicMobs。 */
    private final MythicMobsHook mythicMobs;

    public EntityListener(ProgressService progress, Consumer<ApplyResult> onProgress) {
        this(progress, onProgress, null);
    }

    public EntityListener(ProgressService progress, Consumer<ApplyResult> onProgress, MythicMobsHook mythicMobs) {
        super(progress, onProgress);
        this.mythicMobs = mythicMobs;
    }

    /**
     * 击杀生物 → {@link Trigger#KILL}；{@code getKiller()} 为 null（摔落、岩浆等）时不计。
     * MythicMobs 别名与实体类型名是同一个对象的两个等价标识，必须放进同一个动作——推两次会让「击杀任意生物」计双份。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        push(new ProgressContext(killer, Trigger.KILL, event.getEntityType().name(), 1, null,
                mythicAliases(event.getEntity())));
    }

    /**
     * 实体的 MythicMobs 别名（形如 {@code mythic:SkeletalKnight}）；不是 MythicMobs 怪物时为空表。
     * <p>
     * 兜住 Throwable：MythicMobs 自身出错（版本不匹配、内部异常）不该让这次击杀事件
     * 连同其它任务的进度一起失败。
     */
    private List<String> mythicAliases(Entity entity) {
        if (mythicMobs == null || !(entity instanceof LivingEntity living)) {
            return List.of();
        }
        try {
            String mobId = mythicMobs.mobId(living);
            return (mobId == null || mobId.isBlank())
                    ? List.of()
                    : List.of(MythicMobsHook.PREFIX + mobId);
        } catch (Throwable e) {
            return List.of();
        }
    }

    /**
     * 玩家击杀玩家 → {@link Trigger#KILL}，目标名固定为 {@code PLAYER}。
     * <p>
     * 生物击杀玩家的场景已被 {@link #onDeath} 覆盖，这里只处理 PvP，
     * 并排除自杀（自己杀自己不算击杀成就）。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.equals(event.getEntity())) {
            return;
        }
        push(ProgressContext.of(killer, Trigger.KILL, "PLAYER"));
    }

    /**
     * 垂钓 → {@link Trigger#FISH}。
     * <p>
     * 只认 {@code CAUGHT_FISH} 状态：抛竿、拉空竿、钓到垃圾实体都不是「钓上鱼」。
     * 目标与数量取钓获物品堆本身——堆可能大于 1，按堆大小计，与合成口径一致。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        String target = null;
        int amount = 1;
        if (event.getCaught() instanceof org.bukkit.entity.Item item) {
            ItemStack stack = item.getItemStack();
            target = stack.getType().name();
            amount = Math.max(1, stack.getAmount());
        }
        push(ProgressContext.of(event.getPlayer(), Trigger.FISH, target, amount));
    }

    /**
     * 剪切 → {@link Trigger#SHEAR}。
     * <p>
     * 目标是被剪的实体类型名（如 {@code SHEEP}）；剪毛机等非玩家剪切
     * 不触发本事件，天然不会误计。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        push(ProgressContext.of(event.getPlayer(), Trigger.SHEAR, event.getEntity().getType().name()));
    }

    /**
     * 繁殖 → {@link Trigger#BREED}，目标是幼崽的实体类型名。
     * <p>
     * {@code getBreeder()} 可能不是玩家（插件模拟繁殖），此时不计入任何人。
     */
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

    /**
     * 与实体交互 → {@link Trigger#INTERACT}。
     * <p>
     * 右键实体即算一次交互，{@code mode} 固定为 {@code RIGHT_CLICK_ENTITY}；
     * 对方块的交互（含左键）由 {@link BlockListener} 负责，两处不会重复计。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        push(new ProgressContext(event.getPlayer(), Trigger.INTERACT,
                event.getRightClicked().getType().name(), 1, "RIGHT_CLICK_ENTITY"));
    }
}
