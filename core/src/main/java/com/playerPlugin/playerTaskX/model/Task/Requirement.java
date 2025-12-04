package com.playerPlugin.playerTaskX.model.Task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Requirement {
    private String item;
    private int amount;

    @JsonCreator
    public Requirement(
            @JsonProperty("item") String item,
            @JsonProperty("amount") int amount) {
        this.item = item;
        this.amount = amount;
    }

    public String getItem() {
        return item;
    }
    public void setItem(String material) {
        this.item = material;
    }

    public int getAmount() {
        return amount;
    }
    public void setAmount(int amount) {
        this.amount = amount;
    }
}
