package com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget;

import org.bukkit.Material;

public class MaterialRequirement {
    public Material material;
    public int amount;

    public MaterialRequirement(Material material, int amount) {
        this.material = material;
        this.amount = amount;
    }
}
