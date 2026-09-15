package com.playerPlugin.playerTaskX.core.listener;

import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.core.engine.ApplyResult;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.integration.customcontent.CustomContentHooks;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.function.Consumer;

/**
 * 方块相关动作：挖掘、放置、对方块的交互；自定义方块 id 作为别名随原版材质名一起推。
 * <p>{@link PlayerInteractEvent} 同时承载左键破坏与右键交互，因此左键且可瞬间破坏的方块算挖掘，其余算交互，避免一次操作双计。
 */
public final class BlockListener extends ProgressListener implements Listener {

    /** 自定义内容来源；空实现表示两家都没装（最常见的情况）。 */
    private final CustomContentHooks customContent;

    public BlockListener(ProgressService progress, Consumer<ApplyResult> onProgress,
                         CustomContentHooks customContent) {
        super(progress, onProgress);
        this.customContent = customContent == null ? CustomContentHooks.empty() : customContent;
    }

    /**
     * 挖掘方块 → {@link Trigger#BREAK_BLOCK}。
     * <p>
     * {@code MONITOR + ignoreCancelled} 保证被保护插件取消的破坏（领地、
     * 冒险模式）不会计成进度。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        push(new ProgressContext(event.getPlayer(), Trigger.BREAK_BLOCK, block.getType().name(), 1, null,
                customContent.aliases(block)));
    }

    /**
     * 放置方块 → {@link Trigger#PLACE_BLOCK}。
     * <p>
     * 目标取放置后方块的材质；被取消的放置同样不计。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        push(new ProgressContext(event.getPlayer(), Trigger.PLACE_BLOCK, block.getType().name(), 1, null,
                customContent.aliases(block)));
    }

    /**
     * 对方块的交互 → {@link Trigger#INTERACT} 或 {@link Trigger#BREAK_BLOCK}。
     * <p>
     * 与实体的交互（右键实体）在 {@link EntityListener}，两处不会重复计。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Action action = event.getAction();

        if (block == null) {
            // 空气交互（如右键钓鱼、右键发射器）不计入方块类目标
            return;
        }

        if (action == Action.LEFT_CLICK_BLOCK && isInstantlyBreakable(block.getType())) {
            push(new ProgressContext(player, Trigger.BREAK_BLOCK, block.getType().name(), 1, null,
                    customContent.aliases(block)));
            return;
        }

        String mode = switch (action) {
            case LEFT_CLICK_BLOCK -> "LEFT_CLICK_BLOCK";
            case RIGHT_CLICK_BLOCK -> "RIGHT_CLICK_BLOCK";
            default -> action.name();
        };
        push(new ProgressContext(player, Trigger.INTERACT, block.getType().name(), 1, mode,
                customContent.aliases(block)));
    }

    /**
     * 判断方块是否「一点就碎」。
     * <p>
     * 用硬度近似而不是维护一张硬编码清单：这样新版本方块无需额外维护。
     * 0 硬度覆盖草、花、火把、红石线等。
     */
    private boolean isInstantlyBreakable(Material material) {
        float hardness = material.getHardness();
        return hardness == 0.0f;
    }
}
