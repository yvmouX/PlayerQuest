package com.playerPlugin.playerTaskX.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playerPlugin.playerTaskX.api.Enum.PTXRewardType;

public class RewardDefinition {
    private final String id;
    private final PTXRewardType type;
    private final String content;
    private final int amount;

    @JsonCreator
    public RewardDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("type") PTXRewardType type,
            @JsonProperty("content") String content,
            @JsonProperty("amount") int amount
    ) {
        this.id = id;
        this.type = type;
        this.content = content;
        this.amount = amount;
    }

    public String getId() {
        return id;
    }

    public PTXRewardType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public int getAmount() {
        return amount;
    }

}