package com.playerPlugin.playerTaskX.core.listener;

import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.core.engine.ApplyResult;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
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
        // 一次合成可能产出多个（如 4 个火把），按产物数量计入，避免玩家被要求重复合成
        int amount = Math.max(1, result.getAmount());
        push(ProgressContext.of(player, Trigger.CRAFT, result.getType().name(), amount));
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
