package com.playerPlugin.playerTaskX.event;

import cn.yvmou.ylib.api.logger.Logger;
import com.playerPlugin.playerTaskX.api.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import static com.playerPlugin.playerTaskX.api.Common.Repo_URL;
import static com.playerPlugin.playerTaskX.api.Common.isLatest;

public class PlayerJoinHandler implements Listener {
    private final Logger log;
    private final TaskCache cache;
    private final TaskProgressRepository progressRepository;

    public PlayerJoinHandler(Logger log, TaskCache cache, TaskProgressRepository progressRepository) {
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

        // 建议异步加载，防止卡主线程
        // 这里暂时保持同步调用，后续优化
        cache.loadPlayer(player.getUniqueId(), progressRepository.findAll(player));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cache.unloadPlayer(player.getUniqueId());
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
