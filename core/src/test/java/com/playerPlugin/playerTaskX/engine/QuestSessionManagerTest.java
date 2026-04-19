package com.playerPlugin.playerTaskX.engine;

import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuestSessionManagerTest {
    @Test
    void testCreateAndGetSession() {
        QuestSessionManager manager = new QuestSessionManager();
        UUID playerId = UUID.randomUUID();
        QuestSession session = new QuestSession(playerId, "quest1", "start1");
        
        manager.createSession(session);
        
        assertTrue(manager.getSession(playerId, "quest1").isPresent());
        assertEquals(session, manager.getSession(playerId, "quest1").get());
    }
    
    @Test
    void testRemoveSession() {
        QuestSessionManager manager = new QuestSessionManager();
        UUID playerId = UUID.randomUUID();
        QuestSession session = new QuestSession(playerId, "quest1", "start1");
        
        manager.createSession(session);
        manager.removeSession(playerId, "quest1");
        
        assertFalse(manager.getSession(playerId, "quest1").isPresent());
    }
}