package com.playerPlugin.playerTaskX.core.listener;

import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.progress.ProgressDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.Consumer;

/**
 * 玩家生命周期：登录时载入任务索引与每日任务，退出时释放内存。
 * <p>
 * 这两个动作必须在进出服时成对执行，否则进度索引会随玩家数无界增长。
 */
public final class PlayerListener implements Listener {

    private final ProgressService progress;
    private final ProgressDisplay display;
    private final Consumer<org.bukkit.entity.Player> onJoin;

    public PlayerListener(ProgressService progress, ProgressDisplay display,
                          Consumer<org.bukkit.entity.Player> onJoin) {
        this.progress = progress;
        this.display = display;
        this.onJoin = onJoin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        progress.load(event.getPlayer().getUniqueId());
        if (onJoin != null) {
            onJoin.accept(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        progress.unload(event.getPlayer().getUniqueId());
        // 展示层不再持有任何按玩家的状态（进度直接从仓储读），因此退出时无需清理
    }
}
