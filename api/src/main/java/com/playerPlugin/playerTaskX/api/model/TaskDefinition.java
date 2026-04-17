package com.playerPlugin.playerTaskX.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playerPlugin.playerTaskX.api.model.objective.Objective;
import com.playerPlugin.playerTaskX.api.model.reward.Reward;
import com.playerPlugin.playerTaskX.api.model.condition.Condition;

import java.util.ArrayList;
import java.util.List;

public class TaskDefinition {
    private final String id;
    private final String name;
    private final String description;
    private final List<Objective> objectives;
    private final List<Reward> rewards;
    private final List<Condition> conditions;

    @JsonCreator
    public TaskDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("objectives") List<Objective> objectives,
            @JsonProperty("rewards") List<Reward> rewards,
            @JsonProperty("conditions") List<Condition> conditions
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.objectives = objectives != null ? new ArrayList<>(objectives) : new ArrayList<>();
        this.rewards = rewards != null ? new ArrayList<>(rewards) : new ArrayList<>();
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<Objective> getObjectives() { return objectives; }
    public List<Reward> getRewards() { return rewards; }
    public List<Condition> getConditions() { return conditions; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskDefinition that = (TaskDefinition) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
