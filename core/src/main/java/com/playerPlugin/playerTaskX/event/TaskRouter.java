package com.playerPlugin.playerTaskX.event;

import com.playerPlugin.playerTaskX.model.Task.TaskProgress;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.ArrayList;
import java.util.List;

public class TaskRouter {
    // 玩家正在进行的任务列表 缓存。。。。
    private List<TaskProgress> activeTasks = new ArrayList<>();

    public void dispatchKill(Player player, EntityType type, EntityDeathEvent event) {
        for (TaskProgress task : activeTasks) {
            // 判断
            // task.addProgress(1)
        }
    }
}
