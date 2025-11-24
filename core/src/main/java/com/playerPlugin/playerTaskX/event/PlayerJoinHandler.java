package com.playerPlugin.playerTaskX.event;

import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.domain.Task.TaskProgress;
import com.playerPlugin.playerTaskX.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.storage.StorageFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;

import static com.playerPlugin.playerTaskX.common.Common.Repo_URL;
import static com.playerPlugin.playerTaskX.common.Common.isLatest;

public class PlayerJoinHandler implements Listener {
    private final TaskCache cache;
    private final StorageFactory storageFactory;

    public PlayerJoinHandler(StorageFactory storageFactory, TaskCache cache) {
        this.storageFactory = storageFactory;
        this.cache = cache;
    }

    /**
     * 玩家加入游戏时，从数据库加载其任务进度到缓存
     *
     * @param event 玩家加入事件
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        TaskProgressRepository taskProgressRepository = storageFactory.getProgressRepository();


        Optional<TaskProgress> taskProgress = taskProgressRepository.loadForPlayer(player);

        taskProgress.ifPresent(progress->{
            cache.taskProgressToCache(progress, false);
        });
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
