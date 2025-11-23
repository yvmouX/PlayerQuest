package com.playerPlugin.playerTaskX;

import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import static com.playerPlugin.common.Common.Repo_URL;
import static com.playerPlugin.common.Common.isLatest;

public class PlayerJoinHandler implements Listener {
    private final StorgeManager sm;
    public PlayerJoinHandler(StorgeManager sm) {
        this.sm = sm;
    }

    @EventHandler
    public void onAdminJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("playertaskx.admin") || event.getPlayer().isOp()) {
            if (isLatest) {
                player.sendMessage("§a当前版本为最新版本");
            } else {
                player.sendMessage("§a有新版本待更新，请前往 §b" + Repo_URL + " §a查看新版本");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        sm.databaseToCache(event.getPlayer());
    }
}
