package com.playerPlugin.playerTaskX.api.service;

import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SessionStorage {

    /**
     * 保存玩家任务会话
     * @param session 要保存的会话对象
     */
    void save(QuestSession session);

    /**
     * 查询指定玩家的指定任务会话
     * @param playerId 玩家UUID
     * @param questId 任务ID
     * @return 如果存在则返回会话对象，否则返回空
     */
    Optional<QuestSession> find(UUID playerId, String questId);

    /**
     * 查询指定玩家的所有会话
     * @param playerId 玩家UUID
     * @return 该玩家的所有会话列表
     */
    Collection<QuestSession> findByPlayer(UUID playerId);

    /**
     * 查询所有进行中的会话
     * @return 所有状态为进行中的会话列表
     */
    Collection<QuestSession> findAllActive();

    /**
     * 删除指定玩家的指定任务会话
     * @param playerId 玩家UUID
     * @param questId 任务ID
     */
    void delete(UUID playerId, String questId);
}
