package com.playerPlugin.playerTaskX.core.listener;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.core.daily.DailyService;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.progress.ProgressDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家生命周期：登录时载入任务索引、补发每日任务并刷新展示，退出时释放内存。
 * <p>
 * 载入与释放必须在进出服时成对执行，否则进度索引会随玩家数无界增长。
 */
public final class PlayerListener implements Listener {

    private final ProgressService progress;
    private final ProgressDisplay display;
    private final DailyService daily;
    private final MessageService messages;

    public PlayerListener(ProgressService progress, ProgressDisplay display,
                          DailyService daily, MessageService messages) {
        this.progress = progress;
        this.display = display;
        this.daily = daily;
        this.messages = messages;
    }

    /**
     * 登录：载入进度索引，再补发每日任务（可能跨天），最后刷新进度展示。
     * <p>
     * 顺序不能颠倒——先展示后补发会让玩家看到空列表。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        progress.load(player.getUniqueId());
        if (daily.ensureAssigned(player)) {
            messages.send(player, "daily.reset");
        }
        display.update(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        progress.unload(event.getPlayer().getUniqueId());
        // 展示层不再持有任何按玩家的状态（进度直接从仓储读），因此退出时无需清理
    }
}
