package com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget;

import org.bukkit.Material;

public class Requirement {
    private Material material;
    private int amount;
    private int index;
    private int current;

    public Requirement(Material material, int amount, int index) {
        this.material = material;
        this.amount = amount;
        this.index = index;
    }

    public int getAmount() {
        return amount;
    }
    public void setAmount(int amount) {
        this.amount = amount;
    }

    public int getIndex() {
        return index;
    }
    public void setIndex(int index) {
        this.index = index;
    }

    public Material getMaterial() {
        return material;
    }
    public void setMaterial(Material material) {
        this.material = material;
    }

    public int getCurrent() {
        return current;
    }
    public void setCurrent(int current) {
        this.current = current;
    }
}
