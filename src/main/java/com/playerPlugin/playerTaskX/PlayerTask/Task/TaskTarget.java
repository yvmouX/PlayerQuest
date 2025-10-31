package com.playerPlugin.playerTaskX.PlayerTask.Task;

import com.playerPlugin.playerTaskX.PlayerTask.TaskTargets;

public class TaskTarget {
    private String target_id;
    private TaskTargets action;
    private int count;

    // Getters and Setters
    public String getTarget_id() { return target_id; }
    public void setTarget_id(String target_id) { this.target_id = target_id; }

    public TaskTargets getAction() { return action; }
    public void setAction(TaskTargets action) { this.action = action; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
