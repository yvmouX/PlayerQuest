package com.playerPlugin.playerTaskX.model.Task;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class TaskTrigger {
    @JsonProperty("onTaskStart")
    private List<String> onTaskStart = new ArrayList<>();
    @JsonProperty("onTaskFinish")
    private List<String> onTaskFinish = new ArrayList<>();
    @JsonProperty("onTaskFail")
    private List<String> onTaskFail = new ArrayList<>();

    // Getters and Setters
    public List<String> getOnTaskStart() { return onTaskStart; }
    public void setOnTaskStart(List<String> onTaskStart) { this.onTaskStart = onTaskStart; }

    public List<String> getOnTaskFinish() { return onTaskFinish; }
    public void setOnTaskFinish(List<String> onTaskFinish) { this.onTaskFinish = onTaskFinish; }

    public List<String> getOnTaskFail() { return onTaskFail; }
    public void setOnTaskFail(List<String> onTaskFail) { this.onTaskFail = onTaskFail; }
}
