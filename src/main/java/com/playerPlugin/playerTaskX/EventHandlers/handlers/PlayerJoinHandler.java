package com.playerPlugin.playerTaskX.EventHandlers.handlers;

import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import com.playerPlugin.playerTaskX.exceptions.InvalidTask;
import com.playerPlugin.playerTaskX.utils.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.Objects;

import static com.playerPlugin.playerTaskX.PlayerTask.TaskManager.tm;
import static com.playerPlugin.playerTaskX.consts.common.Repo_URL;
import static com.playerPlugin.playerTaskX.consts.common.isLatest;
import static com.playerPlugin.playerTaskX.utils.Help.logger;

public class PlayerJoinHandler implements Listener {
    @EventHandler
    public void onAdminJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("playertaskx.admin") || event.getPlayer().isOp()) {
            if (isLatest) {
                player.sendMessage(Logger.prefix + "§a当前版本为最新版本");
            } else {
                player.sendMessage(Logger.prefix + "§a有新版本待更新，请前往 §b" + Repo_URL + " §a查看新版本");
            }
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<String> inProgressTaskIds = null;
        try {
            inProgressTaskIds = StorgeManager.getPlayerTaskDAO().getInProgressTaskIds(player.getUniqueId().toString());
        } catch (Exception e) {
            logger.error("加载玩家" + player.getName() + "任务数据时出错！", e);
        }

        if (inProgressTaskIds != null) {
            System.out.println(inProgressTaskIds); // TODO 使用debug消息处理器
            inProgressTaskIds.forEach(taskId -> {
                tm.getPlayerTaskCache().updatePlayerTaskToCache(
                        Objects.requireNonNull(tm.toPlayerTask(player.getUniqueId(), taskId)),
                        false
                );
            });
        }
        player.sendMessage(Logger.prefix + String.format("§a已加载玩家%s任务数据！", player.getName()));
    }
}
