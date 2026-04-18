package com.playerPlugin.playerTaskX.engine;

import com.playerPlugin.playerTaskX.api.model.session.QuestSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QuestSessionManager {
    private final Map<UUID, Map<String, QuestSession>> playerSessions = new ConcurrentHashMap<>();
    
    public void createSession(QuestSession session) {
        playerSessions
            .computeIfAbsent(session.getPlayerId(), k -> new ConcurrentHashMap<>())
            .put(session.getQuestId(), session);
    }
    
    public Optional<QuestSession> getSession(UUID playerId, String questId) {
        Map<String, QuestSession> sessions = playerSessions.get(playerId);
        if (sessions == null) return Optional.empty();
        return Optional.ofNullable(sessions.get(questId));
    }
    
    public Collection<QuestSession> getPlayerSessions(UUID playerId) {
        Map<String, QuestSession> sessions = playerSessions.get(playerId);
        if (sessions == null) return Collections.emptyList();
        return Collections.unmodifiableCollection(sessions.values());
    }
    
    public Collection<QuestSession> getAllSessions() {
        return playerSessions.values().stream()
            .flatMap(m -> m.values().stream())
            .toList();
    }
    
    public void removeSession(UUID playerId, String questId) {
        Map<String, QuestSession> sessions = playerSessions.get(playerId);
        if (sessions != null) {
            sessions.remove(questId);
        }
    }
    
    public void clearPlayerSessions(UUID playerId) {
        playerSessions.remove(playerId);
    }
}
