package com.playerPlugin.playerTaskX.PlayerTask.Task;

import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXTaskStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerTask {
    private UUID uuid;
    private Task task;
    private int progress; // 保留总进度字段，用于向后兼容
    private PTXTaskStatus status;
    // 新增：存储每个项目的进度，格式为 <项目类型, 当前进度>
    private Map<String, Integer> itemProgress;

    public PlayerTask(UUID playerId, Task task) {
        this.uuid = playerId;
        this.task = task;
        this.progress = 0;
        this.status = PTXTaskStatus.IN_PROGRESS;
        this.itemProgress = new HashMap<>();
    }

    // Getter and Setter
    public UUID getUUID() { return this.uuid; }
    public void setUUID(UUID uuid) { this.uuid = uuid; }

    public Task getTask() { return this.task; }
    public void setTask(Task task) { this.task = task; }

    public int getProgress() { return this.progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public PTXTaskStatus getStatus() { return this.status; }
    public void setStatus(PTXTaskStatus status) { this.status = status; }
    
    // 新增：获取项目进度Map
    public Map<String, Integer> getItemProgress() {
        return itemProgress;
    }
    
    // 新增：设置项目进度Map
    public void setItemProgress(Map<String, Integer> itemProgress) {
        this.itemProgress = itemProgress;
    }
    
    /**
     * 获取指定项目的进度
     * @param itemType 项目类型
     * @return 当前进度，如果不存在则返回0
     */
    public int getItemProgress(String itemType) {
        return itemProgress.getOrDefault(itemType, 0);
    }
    
    /**
     * 设置指定项目的进度
     * @param itemType 项目类型
     * @param progress 进度值
     */
    public void setItemProgress(String itemType, int progress) {
        itemProgress.put(itemType, progress);
    }
    
    /**
     * 增加指定项目的进度
     * @param itemType 项目类型
     * @param amount 增加的数量
     */
    public void addItemProgress(String itemType, int amount) {
        int currentProgress = getItemProgress(itemType);
        itemProgress.put(itemType, currentProgress + amount);
    }
    
    /**
     * 检查指定项目是否达到目标进度
     * @param itemType 项目类型
     * @param targetProgress 目标进度
     * @return 是否达到目标
     */
    public boolean isItemComplete(String itemType, int targetProgress) {
        return getItemProgress(itemType) >= targetProgress;
    }
}