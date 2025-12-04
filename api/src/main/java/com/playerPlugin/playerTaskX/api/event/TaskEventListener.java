package com.playerPlugin.playerTaskX.api.event;

/**
 * 任务事件监听器接口
 * 其他插件可以实现此接口来监听任务事件
 */
public interface TaskEventListener {

    /**
     * 当任务开始时调用
     * @param event 任务开始事件
     */
    default void onTaskStart(TaskStartEvent event) {}

    /**
     * 当任务进度更新时调用
     * @param event 任务进度事件
     */
    default void onTaskProgress(TaskProgressEvent event) {}

    /**
     * 当任务完成时调用
     * @param event 任务完成事件
     */
    default void onTaskComplete(TaskCompleteEvent event) {}

    /**
     * 当任务失败时调用
     * @param event 任务失败事件
     */
    default void onTaskFail(TaskFailEvent event) {}
}
