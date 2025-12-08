//package com.playerPlugin.playerTaskX.api.impl;
//
//import com.playerPlugin.playerTaskX.api.task.ITaskProgress;
//import com.playerPlugin.playerTaskX.model.Task.TaskProgress;
//
//import java.util.UUID;
//
///**
// * TaskProgress 适配器
// * 将内部的 TaskProgress 适配到 API 的 ITaskProgress 接口
// */
//public class TaskProgressAdapter implements ITaskProgress {
//    private final TaskProgress taskProgress;
//
//    public TaskProgressAdapter(TaskProgress taskProgress) {
//        this.taskProgress = taskProgress;
//    }
//
//    @Override
//    public UUID getPlayerId() {
//        return taskProgress.getPlayerId();
//    }
//
//    @Override
//    public String getTaskId() {
//        return taskProgress.getTaskId();
//    }
//
//    @Override
//    public int getCurrentProgress() {
//        return taskProgress.getCurrentProgress();
//    }
//
//    @Override
//    public void setCurrentProgress(int progress) {
//        taskProgress.setCurrentProgress(progress);
//    }
//
//    @Override
//    public TaskStatus getStatus() {
//        // 将内部状态映射到 API 状态
//        switch (taskProgress.getStatus()) {
//            case NOT_STARTED:
//                return TaskStatus.NOT_STARTED;
//            case IN_PROGRESS:
//                return TaskStatus.IN_PROGRESS;
//            case COMPLETED:
//                return TaskStatus.COMPLETED;
//            case FAILED:
//                return TaskStatus.FAILED;
//            default:
//                return TaskStatus.CANCELLED;
//        }
//    }
//
//    @Override
//    public void setStatus(TaskStatus status) {
//        // 将 API 状态映射到内部状态
//        switch (status) {
//            case NOT_STARTED:
//                taskProgress.setStatus(com.playerPlugin.playerTaskX.common.Enum.PTXTaskStatus.NOT_STARTED);
//                break;
//            case IN_PROGRESS:
//                taskProgress.setStatus(com.playerPlugin.playerTaskX.common.Enum.PTXTaskStatus.IN_PROGRESS);
//                break;
//            case COMPLETED:
//                taskProgress.setStatus(com.playerPlugin.playerTaskX.common.Enum.PTXTaskStatus.COMPLETED);
//                break;
//            case FAILED:
//                taskProgress.setStatus(com.playerPlugin.playerTaskX.common.Enum.PTXTaskStatus.FAILED);
//                break;
//            case CANCELLED:
//                taskProgress.setStatus(com.playerPlugin.playerTaskX.common.Enum.PTXTaskStatus.CANCELLED);
//                break;
//        }
//    }
//
//    @Override
//    public long getStartTime() {
//        return taskProgress.getStartTime();
//    }
//
//    @Override
//    public long getCompletionTime() {
//        return taskProgress.getCompletionTime();
//    }
//
//    @Override
//    public boolean isCompleted() {
//        return taskProgress.getStatus() == com.playerPlugin.playerTaskX.common.Enum.PTXTaskStatus.COMPLETED;
//    }
//}
