package com.playerPlugin.playerTaskX.EventHandlers.handlers;

import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;

public class CraftEventHandler implements Listener {

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        TaskManager taskManager = TaskManager.getInstance();
        if (taskManager == null) return;

        // 获取合成的物品类型
        String itemType = event.getRecipe().getResult().getType().name().toLowerCase();
        
        // 检查任务进度
        taskManager.checkTaskProgress(player, "craft", itemType);
    }
}
