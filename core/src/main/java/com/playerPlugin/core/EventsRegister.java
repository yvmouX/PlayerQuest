package com.playerPlugin.core;

import com.playerPlugin.bukkit.bridge.BukkitEventBridge;
import com.playerPlugin.core.listeners.KillListener;

public class EventsRegister {
    private final PlayerTaskX plugin;
    private final BukkitEventBridge bridge;

    public EventsRegister(PlayerTaskX plugin, BukkitEventBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    public void register() {
        registerEventHandlers();
        plugin.getServer().getPluginManager().registerEvents(new KillListener(bridge), plugin);
    }

    private void registerEventHandlers() {

    }
}
