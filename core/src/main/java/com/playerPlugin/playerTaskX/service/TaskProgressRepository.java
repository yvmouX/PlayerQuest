package com.playerPlugin.playerTaskX.service;

import com.playerPlugin.playerTaskX.domain.Task.TaskProgress;
import org.bukkit.entity.Player;

import java.util.Optional;

public interface TaskProgressRepository {
    /**
     * 加载指定玩家的任务进度
     *
     * @param player 玩家
     * @return {@link Optional }<{@link TaskProgress }>
     */
    Optional<TaskProgress> loadForPlayer(Player player);
}
