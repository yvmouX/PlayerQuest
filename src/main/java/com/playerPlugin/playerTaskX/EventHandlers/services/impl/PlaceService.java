package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.List;

import static com.playerPlugin.playerTaskX.utils.Help.log;

public class PlaceService implements EventCallback<BlockPlaceEvent> {
    private final TaskManager tm;
    private final StorgeManager sm;

    public PlaceService(TaskManager tm, StorgeManager sm) {
        this.tm = tm;
        this.sm = sm;
    }

    @Override
    public void onEvent(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material placedBlockType = event.getBlockPlaced().getType();

        log.info("Placed block type: " + placedBlockType);

        checkTaskProgress(player, placedBlockType);
    }

    private void checkTaskProgress(Player player, Material placedBlockType) {
        List<PlayerTask> tasks = sm.getCacheDAO().getPlayerTaskListFormCache(player.getUniqueId(), PTXTaskStatus.IN_PROGRESS);
        if (tasks == null) {
            return;
        }
        for (PlayerTask task : tasks) {
            tm.getTaskProgressManger().increasePlayerTaskProgress(task, placedBlockType, 1);
            task.getTask().getTargets().forEach(target -> {
                log.info(String.format("玩家 %s 任务 %s 放置 %d/%d 个 %s", player.getName(), task.getTask().getName(), target.getCurrent(), target.getRequirement().getAmount(), target.getRequirement().getMaterial()));
            });
        }
    }
}
