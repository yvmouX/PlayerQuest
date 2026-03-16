package com.playerPlugin.playerTaskX.event.listeners;

import com.playerPlugin.playerTaskX.api.Enum.PTXActionType;
import com.playerPlugin.playerTaskX.event.TaskRouter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * 监听游戏事件
 *
 * 抽象事件并交给 bridge
 *
 * 不直接操作任务系统（保持干净）
 */
public class KillListener implements Listener {
    private final TaskRouter router;

    public KillListener(TaskRouter router) {
        this.router = router;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null) {
            router.dispatch(killer, PTXActionType.KILL, e.getEntity().getType().name(), 1);
        }
    }
}
