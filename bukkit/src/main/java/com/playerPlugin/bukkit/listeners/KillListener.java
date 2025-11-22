package com.playerPlugin.bukkit.listeners;

import com.paperPlugin.api.events.TaskProgressEvent;
import com.playerPlugin.common.Enum.PTXActionType;
import com.playerPlugin.core.event.EventBus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.UUID;

/**
 * 监听游戏事件
 *
 * 抽象事件并交给 bridge
 *
 * 不直接操作任务系统（保持干净）
 */
public class KillListener implements Listener {
    private final EventBus eventBus;

    public KillListener(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        TaskProgressEvent event = new TaskProgressEvent(
                e.getEntity().getUniqueId(),
                PTXActionType.KILL,
                e.getEntityType().name(),
                1 // 默认每次击杀增加 1
        );
        eventBus.post(e);
    }
}
