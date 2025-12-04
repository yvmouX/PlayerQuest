package com.playerPlugin.playerTaskX.model.Task;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskType;
import com.playerPlugin.playerTaskX.api.task.ITaskCondition;
import com.playerPlugin.playerTaskX.api.task.ITaskDefinition;
import com.playerPlugin.playerTaskX.api.task.ITaskReward;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 任务定义
 *
 * @author yvmoux
 * @date 2025/11/23
 */
public class TaskDefinition implements ITaskDefinition {
    private final String id; // 唯一ID
    private final PTXTaskType type; // 不可变类型
    private String name; // 名称
    private String description;
    public List<TaskCondition> conditions; // TODO
    private List<TaskTarget> targets;
    private TaskTrigger trigger;

    public TaskDefinition(String id, PTXTaskType type, String name, List<TaskTarget> targets, TaskTrigger trigger) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.targets = targets;
        this.trigger = trigger;
    }

    // Getter and Setter methods
    @Override
    public String getId() {
        return id;
    }

    @Override
    public PTXTaskType getType() {
        return type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    public List<TaskTarget> getTargets() {
        return targets;
    }

    public List<TaskCondition> getConditions() {
        return conditions;
    }
}
