package com.playerPlugin.playerTaskX.core.listener;

import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.core.engine.ApplyResult;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
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
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.function.Consumer;

/**
 * 物品域动作：合成、消耗、附魔。
 * <p>
 * 分组依据是「动作围绕一件物品发生」，与事件在 Bukkit 的包归属无关
 * （消耗事件在 player 包、附魔事件在 enchantment 包）。附魔原在
 * EntityListener，属于归位；发言与执行命令与物品无关，归 {@link TextListener}。
 */
public final class ItemListener extends ProgressListener implements Listener {

    public ItemListener(ProgressService progress, Consumer<ApplyResult> onProgress) {
        super(progress, onProgress);
    }

    /**
     * 合成 → {@link Trigger#CRAFT}。
     * <p>
     * 数量是本次合成的<b>实际产出</b>而不是配方单批产量：
     * Shift+点击会一次连做多批，批数计算见 {@link #craftedAmount}。
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
        push(ProgressContext.of(player, Trigger.CRAFT, result.getType().name(), amount));
    }

    /**
     * 计算本次合成实际产出的数量（纯函数，便于脱离服务端测试）。
     * <p>
     * Shift+点击合成时 Bukkit 只触发一次事件，却会按原料连做多批——
     * 例如火把配方一批产 4 个，背包里放 64 煤 + 64 木棍再 Shift 一点，
     * 实际产出 256 个，但事件里的产物数量仍是 4。若只按单批产物计数，
     * 玩家要做远超配置数量的合成才能完成任务。
     * <p>
     * 每个配方格一批只消耗 1 个原料（原版配方皆是如此），因此批数 =
     * 格子里原料最少的那个的数量。背包空间不足时实际产出会小于该值，
     * 进度会略微超前——宁可略多不可少计，少计正是火把工坊 bug 的根因。
     *
     * @param shiftClick   是否为 Shift+点击（一次连做多批）
     * @param matrix       合成格里的原料，空格子为 null 或 AIR
     * @param resultAmount 单批产物数量
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
        push(ProgressContext.of(event.getPlayer(), Trigger.CONSUME, event.getItem().getType().name()));
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
        push(new ProgressContext(event.getEnchanter(), Trigger.ENCHANT, enchantment, 1, event.getItem().getType().name()));
    }

    /** 从配方中取产物；不支持取产物的配方返回 null。 */
    private ItemStack resultOf(Recipe recipe) {
        if (recipe == null) {
            return null;
        }
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.getResult();
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return shapeless.getResult();
        }
        return recipe.getResult();
    }
}
