package com.playerPlugin.playerTaskX.event;

import cn.yvmou.ylib.api.services.LoggerService;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.storage.TaskProgressRepository;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;

import static com.playerPlugin.playerTaskX.Trigger.Common.Repo_URL;
import static com.playerPlugin.playerTaskX.Trigger.Common.isLatest;

public class PlayerJoinHandler implements Listener {
    private final LoggerService log;
    private final TaskCache cache;
    private final TaskProgressRepository progressRepository;

    public PlayerJoinHandler(LoggerService log, TaskCache cache, TaskProgressRepository progressRepository) {
        this.log = log;
        this.cache = cache;
        this.progressRepository = progressRepository;
    }

    /**
     * 玩家加入游戏时，从数据库加载其任务进度到缓存
     *
     * @param event 玩家加入事件
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Optional<TaskProgress> taskProgress = progressRepository.loadForPlayer(player);

        taskProgress.ifPresent(cache::progressToCache);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

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
}
