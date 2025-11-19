package com.playerPlugin.infra.listeners;

import com.playerPlugin.infra.bridge.BukkitEventBridge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 监听游戏事件
 *
 * 抽象事件并交给 bridge
 *
 * 不直接操作任务系统（保持干净）
 */
public class KillListener implements Listener {
    private final BukkitEventBridge eventBridge;

    public KillListener(BukkitEventBridge eventBridge) {
        this.eventBridge = eventBridge;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        eventBridge.pushKillEvent(
                event.getEntity().getUniqueId(),
                event.getEntityType().name()
        );
    }
}
