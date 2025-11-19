package com.paperPlugin.api.model;

import com.playerPlugin.common.Enum.PTXTaskType;

import java.util.List;

public class TaskDefinitionDTO {
    public String id;
    public String name;
    public String description;
    public PTXTaskType type;
    public List<TaskTargetDTO> targets;
    public List<TaskConditionDTO>  conditions;
    public List<TaskTriggerDTO> triggers;
    public List<RewardDTO> rewards;
}
