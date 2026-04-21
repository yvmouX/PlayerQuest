package com.playerPlugin.playerTaskX.engine;

import com.playerPlugin.playerTaskX.api.model.session.QuestSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QuestSessionManager {
    /** 玩家会话缓存：玩家UUID -> (任务ID -> 任务会话) */
    private final Map<UUID, Map<String, QuestSession>> playerSessions = new ConcurrentHashMap<>();
    
    /**
     * 创建任务会话并加入缓存
     * @param session 任务会话
     */
    public void createSession(QuestSession session) {
        playerSessions
            .computeIfAbsent(session.getPlayerId(), k -> new ConcurrentHashMap<>())
            .put(session.getQuestId(), session);
    }
    
    /**
     * 获取玩家的指定任务会话
     * @param playerId 玩家UUID
     * @param questId 任务ID
     * @return 任务会话
     */
    public Optional<QuestSession> getSession(UUID playerId, String questId) {
        Map<String, QuestSession> sessions = playerSessions.get(playerId);
        if (sessions == null) return Optional.empty();
        return Optional.ofNullable(sessions.get(questId));
    }
    
    /**
     * 获取玩家的所有任务会话
     * @param playerId 玩家UUID
     * @return 会话集合（只读）
     */
    public Collection<QuestSession> getPlayerSessions(UUID playerId) {
        Map<String, QuestSession> sessions = playerSessions.get(playerId);
        if (sessions == null) return Collections.emptyList();
        return Collections.unmodifiableCollection(sessions.values());
    }
    
    /** 获取所有玩家的所有任务会话 */
    public Collection<QuestSession> getAllSessions() {
        return playerSessions.values().stream()
            .flatMap(m -> m.values().stream())
            .toList();
    }
    
    /**
     * 移除玩家的指定任务会话
     * @param playerId 玩家UUID
     * @param questId 任务ID
     */
    public void removeSession(UUID playerId, String questId) {
        Map<String, QuestSession> sessions = playerSessions.get(playerId);
        if (sessions != null) {
            sessions.remove(questId);
        }
    }
    
    /**
     * 清除玩家的所有任务会话
     * @param playerId 玩家UUID
     */
    public void clearPlayerSessions(UUID playerId) {
        playerSessions.remove(playerId);
    }
}
