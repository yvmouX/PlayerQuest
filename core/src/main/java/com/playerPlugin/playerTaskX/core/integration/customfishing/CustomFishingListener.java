package com.playerPlugin.playerTaskX.core.integration.customfishing;

import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.core.engine.ApplyResult;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.listener.ProgressListener;
import net.momirealms.customfishing.api.event.FishingLootSpawnEvent;
import net.momirealms.customfishing.api.mechanic.context.Context;
import net.momirealms.customfishing.api.mechanic.context.ContextKeys;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * CustomFishing 钓获监听器：把钓获翻译成 {@link Trigger#CUSTOM_FISH} 动作（目标 = 战利品 id，数量 = 掉落堆大小，附加尺寸）。
 * <p>引用了 CustomFishing 的类型，只能由 {@link CustomFishingHook} 反射创建；没有 id 的原版钓获不推，否则会和原版 fish 目标计两次。
 */
final class CustomFishingListener extends ProgressListener implements Listener {

    CustomFishingListener(ProgressService progress, Consumer<ApplyResult> onProgress) {
        super(progress, onProgress);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLoot(FishingLootSpawnEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Context<Player> context = event.getContext();
        if (context == null) {
            return;
        }
        String id = context.arg(ContextKeys.ID);
        if (id == null || id.isBlank()) {
            return;
        }
        push(new ProgressContext(player, Trigger.CUSTOM_FISH, id, amountOf(event), sizeOf(context)));
    }

    /** 掉落物堆大小；不是物品实体（例如生成的展示实体）时按 1 计。 */
    private static int amountOf(FishingLootSpawnEvent event) {
        if (event.getEntity() instanceof Item item) {
            ItemStack stack = item.getItemStack();
            if (stack != null) {
                return Math.max(1, stack.getAmount());
            }
        }
        return 1;
    }

    /** 尺寸以字符串形式放进 extra：目标类型自己解析，引擎与上下文不必认识「尺寸」这个概念。 */
    private static String sizeOf(Context<Player> context) {
        Float size = context.arg(ContextKeys.SIZE);
        return size == null ? null : String.valueOf(size);
    }
}
