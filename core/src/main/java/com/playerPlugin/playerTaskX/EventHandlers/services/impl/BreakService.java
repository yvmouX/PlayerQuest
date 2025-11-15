package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import com.playerPlugin.playerTaskX.domain.PlayerTask.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.domain.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.domain.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.List;

import static com.playerPlugin.playerTaskX.utils.Help.log;


public class BreakService implements EventCallback<BlockBreakEvent> {
    private final TaskManager tm;
    private final StorgeManager sm;

    public BreakService(TaskManager tm, StorgeManager sm) {
        this.tm = tm;
        this.sm = sm;
    }

    @Override
    public void onEvent(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material brokenBlockType = event.getBlock().getType();

        log.info("Broken block type: " + brokenBlockType);

        checkTaskProgress(player, brokenBlockType);
    }

    private void checkTaskProgress(Player player, Material brokenBlockType) {
        // TODO 可以把这个检测做成一个方法，避免重复
        List<PlayerTask> tasks = sm.getCacheDAO().getPlayerTaskListFormCache(player.getUniqueId(), PTXTaskStatus.IN_PROGRESS);
        if (tasks == null) {
            return;
        }

        for (PlayerTask task : tasks) {
            tm.getTaskProgressManger().increasePlayerTaskProgress(task, brokenBlockType, 1);
            task.getTask().getTargets().forEach(target -> {
                log.info(String.format("玩家 %s 任务 %s 破坏 %d/%d 个 %s", player.getName(), task.getTask().getName(), target.getCurrent(), target.getRequirement().getAmount(), target.getRequirement().getMaterial()));
            });
        }
    }
}
