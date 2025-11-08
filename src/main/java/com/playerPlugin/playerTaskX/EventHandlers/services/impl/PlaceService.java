package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.List;

import static com.playerPlugin.playerTaskX.PlayerTask.TaskManager.tm;
import static com.playerPlugin.playerTaskX.utils.Help.logger;

public class PlaceService implements EventCallback<BlockPlaceEvent> {
    @Override
    public void onEvent(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material placedBlockType = event.getBlockPlaced().getType();

        logger.info("Placed block type: " + placedBlockType);

        checkTaskProgress(player, placedBlockType);
    }

    private void checkTaskProgress(Player player, Material placedBlockType) {
        List<PlayerTask> tasks = tm.getPlayerTaskCache().getPlayerInProgressTasks(player.getUniqueId());
        if (tasks == null) {
            return;
        }
        for (PlayerTask task : tasks) {
            tm.getTaskProgressManger().increasePlayerTaskProgress(task, placedBlockType, 1);
            task.getTask().getTargets().forEach(target -> {
                logger.info(String.format("玩家 %s 任务 %s 放置 %d/%d 个 %s", player.getName(), task.getTask().getName(), target.getCurrent(), target.getRequirement().getAmount(), target.getRequirement().getMaterial()));
            });
        }
    }
}
