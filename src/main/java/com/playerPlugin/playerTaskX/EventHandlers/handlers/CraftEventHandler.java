package com.playerPlugin.playerTaskX.EventHandlers.handlers;

import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.structs.eventStructs.CraftEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;

public class CraftEventHandler implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public CraftEvent onCraftItem(CraftItemEvent event) {
        if (event.isCancelled()) return null;

        if (!(event.getWhoClicked() instanceof Player player)) {
            return null;
        }

        TaskManager taskManager = TaskManager.getInstance();
        if (taskManager == null) return null;

        // 获取合成的物品类型
        String itemType = event.getRecipe().getResult().getType().name().toLowerCase();
        
        // 检查任务进度
        //taskManager.checkTaskProgress(player, "craft", itemType);
        taskManager.checkTaskProgress(player, "craft", itemType);

        return new CraftEvent(
                player,
                event.getInventory().getResult()
        );
    }
}
