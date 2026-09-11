package com.playerPlugin.playerTaskX.core.listener;

import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.core.engine.ApplyResult;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.function.Consumer;

/**
 * 文本输入类动作：发言、执行命令。
 * <p>
 * 从 ItemListener 独立出来：这两个动作与「物品」毫无关系，塞在物品域里
 * 只会让类名撒谎。独立成类后，「玩家输入了什么」相关的动作有了唯一归宿。
 */
public final class TextListener extends ProgressListener implements Listener {

    public TextListener(ProgressService progress, Consumer<ApplyResult> onProgress) {
        super(progress, onProgress);
    }

    /**
     * 发言 → {@link Trigger#CHAT}。
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
     * 执行命令 → {@link Trigger#COMMAND}。
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
}
