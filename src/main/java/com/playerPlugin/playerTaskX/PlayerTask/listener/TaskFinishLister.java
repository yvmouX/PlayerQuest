package com.playerPlugin.playerTaskX.PlayerTask.listener;

import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.TaskTarget;

import static com.playerPlugin.playerTaskX.utils.Help.logger;

public class TaskFinishLister {
        public void onFinish(TaskTarget task) {
                logger.info(String.format("任务完成：%s x %d", task.getRequirement().getMaterial(), task.getRequirement().getAmount()));
        }
}
