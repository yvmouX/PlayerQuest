package com.playerPlugin.bukkit.listeners;

import com.playerPlugin.bukkit.PlayerTaskX;
import com.playerPlugin.bukkit.bridge.BukkitEventBridge;

public class EventsRegister {
    private final PlayerTaskX plugin;
    private final BukkitEventBridge bridge;

    public EventsRegister(PlayerTaskX plugin, BukkitEventBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(new KillListener(bridge), plugin);
    }
}
