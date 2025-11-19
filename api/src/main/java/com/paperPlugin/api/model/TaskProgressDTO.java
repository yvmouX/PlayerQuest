package com.paperPlugin.api.model;

import com.playerPlugin.common.Enum.PTXTaskStatus;

import java.util.UUID;

public class TaskProgressDTO {
    public UUID uuid;
    public TaskDefinitionDTO task;
    public PTXTaskStatus status;
}
