package com.paperPlugin.api.model;

import com.playerPlugin.common.Enum.PTXTaskStatus;

public class TaskProgressDTO {
    public String uuid;
    public TaskDefinitionDTO task;
    public PTXTaskStatus status;
}
