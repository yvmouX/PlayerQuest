package com.playerPlugin.infra.adapter;

/**
 * 提供统一接口，与 Bukkit Player 解耦
 *
 * 方便未来迁移到 Sponge / Folia / Velocity
 *
 * 提供内部抽象 PlayerAdapter
 */
public class BukkitPlayerAdapter {
    public boolean isOnline(UUID playerId) {
        return Bukkit.getPlayer(playerId) != null;
    }

    public String getName(UUID playerId) {
        Player p = Bukkit.getPlayer(playerId);
        return p != null ? p.getName() : "Unknown";
    }

    public void sendMessage(UUID playerId, String msg) {
        Player p = Bukkit.getPlayer(playerId);
        if (p != null) {
            p.sendMessage(msg);
        }
    }

    public Object getRawPlayer(UUID playerId) {
        // 内部通道保留，必要时传回 Bukkit 原始对象
        return Bukkit.getPlayer(playerId);
    }
}
