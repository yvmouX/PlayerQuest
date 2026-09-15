package com.playerPlugin.playerTaskX.core.listener;

import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.core.engine.ApplyResult;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.integration.customcontent.CustomContentHooks;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.function.Consumer;

/** 物品域动作：合成、消耗、附魔；分组依据是「动作围绕一件物品发生」，与事件在 Bukkit 的哪个包无关。 */
public final class ItemListener extends ProgressListener implements Listener {

    /** 自定义内容来源；空实现表示 ItemsAdder / CraftEngine 都没装。 */
    private final CustomContentHooks customContent;

    public ItemListener(ProgressService progress, Consumer<ApplyResult> onProgress,
                        CustomContentHooks customContent) {
        super(progress, onProgress);
        this.customContent = customContent == null ? CustomContentHooks.empty() : customContent;
    }

    /**
     * 合成 → {@link Trigger#CRAFT}，数量取本次实际产出（Shift+点击会连做多批，见 {@link #craftedAmount}）。
     * 产物是自定义物品时带上它的 id 别名。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Recipe recipe = event.getRecipe();
        ItemStack result = resultOf(recipe);
        if (result == null) {
            return;
        }
        int amount = craftedAmount(event.isShiftClick(),
                event.getInventory().getMatrix(), result.getAmount());
        push(new ProgressContext(player, Trigger.CRAFT, result.getType().name(), amount, null,
                customContent.aliases(result)));
    }

    /**
     * 计算本次合成的实际产出（纯函数，便于脱离服务端测试）：批数 = 合成格里原料最少的那个的数量。
     * Shift+点击只触发一次事件却连做多批，按单批产物计数会严重少计；背包放不下时会略多计——宁可略多不可少计。
     */
    static int craftedAmount(boolean shiftClick, ItemStack[] matrix, int resultAmount) {
        if (!shiftClick) {
            return Math.max(1, resultAmount);
        }
        int batches = Integer.MAX_VALUE;
        for (ItemStack ingredient : matrix) {
            // 不用 Material#isAir：它内部要走注册表，纯数据场景（单测）下会初始化失败；
            // 空物品堆实际只可能是 null / AIR / 数量为 0
            if (ingredient != null && ingredient.getType() != Material.AIR
                    && ingredient.getAmount() > 0) {
                batches = Math.min(batches, ingredient.getAmount());
            }
        }
        if (batches == Integer.MAX_VALUE) {
            // 拿不到合成格（理论上进 CraftItemEvent 不会发生），退回单批数量
            return Math.max(1, resultAmount);
        }
        return Math.max(1, resultAmount * batches);
    }

    /**
     * 消耗（吃喝）→ {@link Trigger#CONSUME}。
     * <p>
     * 一次事件恰好消耗一件物品，数量恒为 1，无需额外计算。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        push(new ProgressContext(event.getPlayer(), Trigger.CONSUME, item.getType().name(), 1, null,
                customContent.aliases(item)));
    }

    /**
     * 附魔 → {@link Trigger#ENCHANT}。
     * <p>
     * 一次附魔可能附加多个魔咒，取第一个作为目标判定依据，数量按 1 计；
     * {@code extra} 带上被附魔的物品类型，供需要区分「给什么附魔」的目标使用。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        String enchantment = event.getEnchantsToAdd().keySet().stream()
                .findFirst()
                .map(key -> key.getKey().getKey())
                .orElse(null);
        ItemStack item = event.getItem();
        push(new ProgressContext(event.getEnchanter(), Trigger.ENCHANT, enchantment, 1,
                item.getType().name(), customContent.aliases(item)));
    }

    /** 从配方中取产物；不支持取产物的配方返回 null。 */
    private ItemStack resultOf(Recipe recipe) {
        // ShapedRecipe / ShapelessRecipe 都实现了 Recipe#getResult，因此不需要分派
        return recipe == null ? null : recipe.getResult();
    }
}
