package com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget;

import org.bukkit.Material;

public class Requirement {
    public Material material;
    public int amount;

    public Requirement(Material material, int amount) {
        this.material = material;
        this.amount = amount;
    }
}
