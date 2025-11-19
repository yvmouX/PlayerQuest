package com.playerPlugin.infra.bridge;

import com.paperPlugin.api.events.TaskProgressEvent;
import com.playerPlugin.core.event.EventBus;

import java.util.UUID;

/**
 * Bridge 层负责把 Bukkit Listener 事件 → 内部事件系统
 *
 * Adapter、Bridge、Listeners 三层配合实现完全解耦
 *
 * 未来可做到：
 *
 * 任务系统独立运行
 *
 * Bukkit/Folia/Velocity/Sponge 可共享核心逻辑
 */
public class BukkitEventBridge {

    private final EventBus eventBus;

    public BukkitEventBridge(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void pushKillEvent(UUID playerId, String mobType) {
        TaskProgressEvent event = new TaskProgressEvent(
                playerId,
                "KILL_MOB",
                mobType,
                1 // 默认每次击杀增加 1
        );
        eventBus.post(event);
    }

    public void pushBreakEvent(UUID playerId, String blockType) {
        TaskProgressEvent event = new TaskProgressEvent(
                playerId,
                "BREAK_BLOCK",
                blockType,
                1
        );
        eventBus.post(event);
    }

    public void pushCraftEvent(UUID playerId, String itemType) {
        TaskProgressEvent event = new TaskProgressEvent(
                playerId,
                "CRAFT_ITEM",
                itemType,
                1
        );
        eventBus.post(event);
    }
}
