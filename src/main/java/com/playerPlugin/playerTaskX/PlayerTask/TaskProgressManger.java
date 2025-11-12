package com.playerPlugin.playerTaskX.PlayerTask;

import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.Requirement;
import org.bukkit.Material;

import static com.playerPlugin.playerTaskX.utils.Help.sm;

public class TaskProgressManger {
    public TaskProgressManger() {

    }

    /**
     * 增加玩家任务进度
     *
     * @param playerTask 玩家任务
     * @param material   材料
     * @param progress   进展数量
     */
    public void increasePlayerTaskProgress(PlayerTask playerTask, Material material, int progress) {
        playerTask.getTask().getTargets().forEach(target -> {
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
                                sm.getCacheDAO().addMaybeFinishedPlayer(playerTask);
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
}
