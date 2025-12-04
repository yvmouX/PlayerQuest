package com.playerPlugin.playerTaskX.api;

import com.playerPlugin.playerTaskX.api.event.TaskEventListener;
import com.playerPlugin.playerTaskX.api.task.ITaskDefinition;
import com.playerPlugin.playerTaskX.api.task.ITaskProgress;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PlayerTaskX 主 API 接口
 * 其他插件通过此接口与 PlayerTaskX 交互
 */
public interface PlayerTaskXAPI {

    /**
     * 获取 API 实例
     * @return API 实例
     * @throws IllegalStateException 如果 PlayerTaskX 未加载
     */
    static PlayerTaskXAPI getInstance() {
        return PlayerTaskXProvider.getApi();
    }

    /**
     * 创建新任务
     * @param taskDefinition 任务定义
     * @return 是否创建成功
     */
    boolean createTask(ITaskDefinition taskDefinition);

    /**
     * 删除任务
     * @param taskId 任务 ID
     * @return 是否删除成功
     */
    boolean deleteTask(String taskId);

    /**
     * 根据 ID 获取任务定义
     * @param taskId 任务 ID
     * @return 任务定义（如果存在）
     */
    Optional<ITaskDefinition> getTaskDefinition(String taskId);

    /**
     * 获取所有任务定义
     * @return 任务定义列表
     */
    List<ITaskDefinition> getAllTaskDefinitions();

    /**
     * 为玩家创建任务进度
     * @param player 玩家
     * @param taskId 任务 ID
     * @return 是否创建成功
     */
    boolean createTaskProgress(Player player, String taskId);

    /**
     * 获取玩家的任务进度
     * @param playerId 玩家 UUID
     * @param taskId 任务 ID
     * @return 任务进度（如果存在）
     */
    Optional<ITaskProgress> getTaskProgress(UUID playerId, String taskId);

    /**
     * 获取玩家的所有任务进度
     * @param playerId 玩家 UUID
     * @return 任务进度列表
     */
    List<ITaskProgress> getAllTaskProgress(UUID playerId);

    /**
     * 更新玩家任务进度
     * @param playerId 玩家 UUID
     * @param taskId 任务 ID
     * @param progress 进度值
     * @return 是否更新成功
     */
    boolean updateTaskProgress(UUID playerId, String taskId, int progress);

    /**
     * 增加玩家任务进度
     * @param playerId 玩家 UUID
     * @param taskId 任务 ID
     * @param amount 增加的数量
     * @return 是否更新成功
     */
    boolean incrementTaskProgress(UUID playerId, String taskId, int amount);

    /**
     * 完成玩家任务
     * @param playerId 玩家 UUID
     * @param taskId 任务 ID
     * @return 是否完成成功
     */
    boolean completeTask(UUID playerId, String taskId);

    /**
     * 重置玩家任务
     * @param playerId 玩家 UUID
     * @param taskId 任务 ID
     * @return 是否重置成功
     */
    boolean resetTask(UUID playerId, String taskId);

    /**
     * 检查玩家是否完成任务
     * @param playerId 玩家 UUID
     * @param taskId 任务 ID
     * @return 是否完成
     */
    boolean isTaskCompleted(UUID playerId, String taskId);

    /**
     * 注册任务事件监听器
     * @param listener 事件监听器
     */
    void registerEventListener(TaskEventListener listener);

    /**
     * 注销任务事件监听器
     * @param listener 事件监听器
     */
    void unregisterEventListener(TaskEventListener listener);

    /**
     * 重新加载插件配置和任务
     */
    void reload();

    /**
     * 获取 API 版本
     * @return API 版本号
     */
    String getApiVersion();
}
