package com.paperPlugin.api.dto;

import java.util.List;

public class TaskDefinitionDTO {
    public String id;
    public String name;
    public String description;
    public String type;
    public List<TaskTargetDTO> targets;
    public List<TaskConditionDTO>  conditions;
    public List<TaskTriggerDTO> triggers;
    public List<RewardDTO> rewards;
}
