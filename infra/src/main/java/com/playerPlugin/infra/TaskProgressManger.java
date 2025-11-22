package com.playerPlugin.infra;

import com.playerPlugin.core.domain.Task.TaskProgress;
import com.playerPlugin.core.domain.Task.TaskDefinition;
import com.playerPlugin.core.domain.Task.Requirement;
import com.playerPlugin.core.domain.Trigger.TaskTriggerExecutor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class TaskProgressManger {
    private final com.playerPlugin.infra.dataManager.StorgeManager sm;
    public TaskProgressManger(com.playerPlugin.infra.dataManager.StorgeManager sm) {
        this.sm = sm;
    }

    /**
     * 增加玩家任务进度
     *
     * @param taskProgress 玩家任务
     * @param material   材料
     * @param progress   进展数量
     */
    public void increasePlayerTaskProgress(TaskProgress taskProgress, Material material, int progress) {
        taskProgress.getTask().getTargets().forEach(target -> {
            switch (target.getAction()) {
                case DROP ->  {}
                case TAKE -> {}
                case KILL -> {}
                case TAME -> {}
                case BREAK -> {}
                case CRAFT -> {}
                case BREED -> {}
                case PLACE -> {
                    Requirement r = target.getRequirement();
                        if (r.getMaterial() == material) {
                            if (target.incrementCurrent(progress)) {
                                sm.getCacheDAO().addMaybeFinishedPlayer(taskProgress);
                            }
                        }
                }
                case CONSUME -> {}
                case ENCHANT -> {}
                case FISHING -> {}
                case SCISSOR -> {}
                case TRIGGER -> {}
                case NONE -> {}
            }
        });

    }

    /**
     * 开始任务
     *
     * @param player 选手
     * @param taskId 任务 ID
     */
    public void startTask(Player player, String taskId) throws SQLException {
        UUID uuid = player.getUniqueId();
        TaskDefinition taskDefinition = tm.getTask(taskId);

        if (taskDefinition == null) {
            player.sendMessage("§c任务 %s 不存在", taskId);
            return;
        };

        // 直接从 数据库 获取玩家进行中的任务列表
        // 检测是否已经有该任务。
        AtomicBoolean canStart = new AtomicBoolean(true);
        for (String id : sm.getTaskIdListFromDatabase(uuid, PTXTaskStatus.IN_PROGRESS)) {
            if (id.equals(taskId)) {
                player.sendMessage("§c你已经接受或者完成过任务: §e" + taskDefinition.getName() + "！");
                canStart.set(false);
            }
        }

        // 向缓存添加任务
        if (canStart.get()) {
            sm.getCacheDAO().addPlayerTaskToCache(List.of(new TaskProgress(uuid, taskDefinition)), true);
        }

        // 执行任务开始触发器
        TaskTriggerExecutor.execute(player, taskDefinition.getTrigger().getOnTaskStart(), taskDefinition);

        player.sendMessage("§a你已开始任务: §e" + taskDefinition.getName());
    }
}
