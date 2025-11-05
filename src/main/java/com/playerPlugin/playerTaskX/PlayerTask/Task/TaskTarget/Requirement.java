package com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget;

import com.playerPlugin.playerTaskX.PlayerTask.listener.RequirementFinishListener;
import org.bukkit.Material;

public class Requirement {
    private Material material;
    private int amount;
    private int index;
    private int current;
    private boolean finished = false;

    private RequirementFinishListener listener;

    public Requirement(Material material, int amount, int index) {
        this.material = material;
        this.amount = amount;
        this.index = index;
    }

    public boolean isFinished() {
        return finished;
    }
    public void setFinished(boolean finished) {
        if (!this.finished && finished) {
            if (listener != null) {
                listener.onFinish(this);
            }
        }
        this.finished = finished;
    }

    public void setListener(RequirementFinishListener listener) {
        this.listener = listener;
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
