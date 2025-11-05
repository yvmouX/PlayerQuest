package com.playerPlugin.playerTaskX.PlayerTask.listener;

import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.Requirement;

public class TaskFinishLister {
        public void onFinish(Requirement task) {
            System.out.println("任务完成：" + task.getMaterial() + " x " + task.getAmount());
        }
}
