package com.playerPlugin.playerTaskX.PlayerTask.listener;

import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.TaskTarget;

public class TaskFinishLister {
        public void onFinish(TaskTarget task) {
            System.out.println("任务完成：" + task.getRequirement().getMaterial() + " x " + task.getRequirement().getAmount());
        }
}
