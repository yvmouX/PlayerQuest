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
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.function.Consumer;

/**
 * 物品与文本相关动作：合成、消耗、发言、执行命令。
 */
public final class ItemListener extends ProgressListener implements Listener {

    public ItemListener(ProgressService progress, Consumer<ApplyResult> onProgress) {
        super(progress, onProgress);
    }

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
     * 进度会略微超前——宁可略多不可少计，少计正是本次要修的 bug。
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        push(ProgressContext.of(event.getPlayer(), Trigger.CONSUME, event.getItem().getType().name()));
    }

    /**
     * 发言。
     * <p>
     * 用 {@link AsyncPlayerChatEvent}（Spigot 标准）而非 Paper 的 AsyncChatEvent：
     * 后者不在 spigot-api 中，直接用会导致插件在 Spigot 上无法加载。
     * 该事件是<b>异步</b>的，因此这里只做纯内存的进度判定，不触碰任何 Bukkit 世界 API；
     * 需要发消息/改物品的动作由主线程后续完成。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        push(ProgressContext.of(event.getPlayer(), Trigger.CHAT, message));
    }

    /**
     * 执行命令。
     * <p>
     * 目标统一归一化为<b>不带前导斜杠</b>的命令名（与目标类型的 schema 默认值一致），
     * 否则 {@code /home} 与配置里的 {@code home} 永远匹配不上。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) {
            return;
        }
        String withoutSlash = raw.startsWith("/") ? raw.substring(1) : raw;
        // 去掉参数，只保留命令本身
        int space = withoutSlash.indexOf(' ');
        String command = space > 0 ? withoutSlash.substring(0, space) : withoutSlash;
        if (command.isBlank()) {
            return;
        }
        push(ProgressContext.of(event.getPlayer(), Trigger.COMMAND, command));
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
