package com.playerPlugin.playerTaskX.PlayerTask;

import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.Requirement;
import com.playerPlugin.playerTaskX.PlayerTask.listener.RequirementFinishListener;
import org.bukkit.Material;

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
                    target.getRequires().forEach(targetRequires -> {
                        if (targetRequires.getMaterial() == material) {
                            targetRequires.setCurrent(targetRequires.getCurrent() + progress);
                            if (targetRequires.getCurrent() >= targetRequires.getAmount()) {
                                targetRequires.setListener(requirement -> {
                                    TaskManager.getInstance().getPlayerTaskCache().addFinishedPlayer(playerTask.getUUID());
                                });
                               targetRequires.setFinished(true);
                            }
                        }
                    });
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
