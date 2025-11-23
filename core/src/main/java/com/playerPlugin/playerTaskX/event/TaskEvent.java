package com.playerPlugin.playerTaskX.event;

import com.playerPlugin.playerTaskX.domain.Task.TaskProgress;
import com.playerPlugin.playerTaskX.service.TaskProgressRepository;
import com.playerPlugin.playerTaskX.storage.StorageFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;

public class TaskEvent implements Listener {
    private final StorageFactory storageFactory;

    public TaskEvent(StorageFactory storageFactory) {
        this.storageFactory = storageFactory;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        TaskProgressRepository taskProgressRepository = storageFactory.getProgressRepository();


        Optional<TaskProgress> taskDefinition = taskProgressRepository.loadForPlayer(player);

        if (taskDefinition.isPresent()) {
            // TODO 添加到内存
        }
    }
}
