package com.playerPlugin.playerTaskX.model.Task;

public class Requirement {
    private String item;
    private int amount;

    public Requirement(String item, int amount) {
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
