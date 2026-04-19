package com.playerPlugin.playerTaskX.engine;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestSessionTest {
    @Test
    void testSessionCreation() {
        UUID playerId = UUID.randomUUID();
        QuestSession session = new QuestSession(playerId, "quest1", "start1");
        
        assertEquals(playerId, session.getPlayerId());
        assertEquals("quest1", session.getQuestId());
        assertEquals("start1", session.getCurrentNodeId());
        assertEquals(PTXTaskStatus.IN_PROGRESS, session.getStatus());
        assertTrue(session.getCompletedNodes().isEmpty());
    }
    
    @Test
    void testMarkNodeCompleted() {
        QuestSession session = new QuestSession(UUID.randomUUID(), "quest1", "start1");
        session.markNodeCompleted("start1");
        
        assertTrue(session.getCompletedNodes().contains("start1"));
    }
    
    @Test
    void testContextUpdate() {
        QuestSession session = new QuestSession(UUID.randomUUID(), "quest1", "start1");
        session.updateContext(java.util.Map.of("counter", 5));
        
        assertEquals(5, session.getContext().get("counter"));
    }
}