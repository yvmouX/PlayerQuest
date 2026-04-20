package com.playerPlugin.playerTaskX.api.service;

import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SessionStorage {
    void save(QuestSession session);
    Optional<QuestSession> find(UUID playerId, String questId);
    Collection<QuestSession> findByPlayer(UUID playerId);
    Collection<QuestSession> findAllActive();
    void delete(UUID playerId, String questId);
}
