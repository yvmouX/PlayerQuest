package com.paperPlugin.api.dto;

import java.util.ArrayList;
import java.util.List;

public class TaskTriggerDTO {
    public List<String> onTaskStart = new ArrayList<>();
    public List<String> onTaskFinish = new ArrayList<>();
    public List<String> onTaskFail = new ArrayList<>();
}
