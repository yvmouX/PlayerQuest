package com.playerPlugin.playerTaskX.PlayerTask;

import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.Requirement;
import org.bukkit.Material;

import static com.playerPlugin.playerTaskX.utils.Help.sm;

public class TaskProgressManger {
    public TaskProgressManger() {

    }

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
                            target.setCurrent(target.getCurrent() + progress);
                            if (target.getCurrent() >= r.getAmount()) {
                                target.setListener(taskTarget -> {
                                    sm.getCacheDAO().addMaybeFinishedPlayer(playerTask.getUUID());
                                });
                                target.setFinished(true);
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
