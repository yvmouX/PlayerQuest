package com.playerPlugin.playerTaskX.storage;

import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public interface TaskProgressRepository {
    /**
     * Find task progress for a player
     *
     * @param player Player
     * @param taskId Task ID
     * @return {@link Optional }<{@link TaskProgress }>
     */
    Optional<TaskProgress> find(UUID player, String taskId);


    /**
     * Save task progress
     *
     * @param progress TaskProgress
     */
    void save(TaskProgress progress);

    /**
     * Delete task progress
     *
     * @param player Player
     * @param taskId Task ID
     */
    void delete(UUID player, String taskId);

    /**
     * 创建指定玩家的任务进度
     * <p>
     *     该方法会直接将任务进度写入仓库
     *     如果玩家的任务进度文件已存在，会记录警告日志并返回。
     * </p>
     *
     * @param player 玩家
     * @param taskDefinition 任务定义
     */
    void createForPlayer(Player player, TaskDefinition taskDefinition);

    /**
     * 加载指定玩家的任务进度
     *
     * @param player 玩家
     * @return {@link Optional }<{@link TaskProgress }>
     */
    Optional<TaskProgress> loadForPlayer(Player player);
}
