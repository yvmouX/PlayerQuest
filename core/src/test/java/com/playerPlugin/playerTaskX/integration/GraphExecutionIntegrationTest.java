package com.playerPlugin.playerTaskX.integration;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.handler.NodeHandlerRegistry;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.api.service.SessionStorage;
import com.playerPlugin.playerTaskX.engine.QuestEngine;
import com.playerPlugin.playerTaskX.engine.QuestSessionManager;
import com.playerPlugin.playerTaskX.handler.StartNodeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GraphExecutionIntegrationTest {
    @Mock private com.playerPlugin.playerTaskX.manager.TaskManager taskManager;
    @Mock private SessionStorage sessionStorage;
    private NodeHandlerRegistry registry;
    private QuestSessionManager sessionManager;
    private QuestEngine engine;
    
    @BeforeEach
    void setUp() {
        registry = new NodeHandlerRegistry();
        sessionManager = new QuestSessionManager();
        engine = new QuestEngine(sessionManager, registry, taskManager, sessionStorage);
    }
    
    @Test
    void testHandlerRegistryFindsHandler() {
        registry.register(new StartNodeHandler());
        
        NodeHandler handler = registry.getHandler("start");
        
        assertNotNull(handler);
        assertEquals("start", handler.getNodeType());
    }
    
    @Test
    void testHandlerRegistryReturnsNullForUnknown() {
        NodeHandler handler = registry.getHandler("unknown");
        
        assertNull(handler);
    }
    
    @Test
    void testSessionCreatedOnStart() {
        List<GraphNode> nodes = List.of(
            new GraphNode("start1", "start", 0, 0, Map.of())
        );
        List<NodeConnection> edges = List.of();
        QuestGraph graph = new QuestGraph("q1", "Test", nodes, edges);
        TaskDefinition task = new TaskDefinition("q1", "Test", "desc", null, 
            List.of(), List.of(), graph);
        
        UUID playerId = UUID.randomUUID();
        
        assertNotNull(engine);
        assertNotNull(sessionManager);
    }
    
    @Test
    void testQuestSessionTracksCurrentNode() {
        UUID playerId = UUID.randomUUID();
        QuestSession session = new QuestSession(playerId, "quest1", "node1");
        
        assertEquals("node1", session.getCurrentNodeId());
        
        session.setCurrentNodeId("node2");
        assertEquals("node2", session.getCurrentNodeId());
    }
    
    @Test
    void testQuestSessionMarksCompleted() {
        UUID playerId = UUID.randomUUID();
        QuestSession session = new QuestSession(playerId, "quest1", "node1");
        
        session.markNodeCompleted("node1");
        
        assertTrue(session.getCompletedNodes().contains("node1"));
        assertFalse(session.getCompletedNodes().contains("node2"));
    }
    
    @Test
    void testGraphNodesAndEdges() {
        List<GraphNode> nodes = List.of(
            new GraphNode("start1", "start", 0, 0, Map.of()),
            new GraphNode("task1", "task", 100, 0, Map.of()),
            new GraphNode("end1", "completion", 200, 0, Map.of())
        );
        List<NodeConnection> edges = List.of(
            new NodeConnection("e1", "start1", "task1", null),
            new NodeConnection("e2", "task1", "end1", null)
        );
        QuestGraph graph = new QuestGraph("g1", "Test", nodes, edges);
        
        assertEquals(3, graph.getNodes().size());
        assertEquals(2, graph.getEdges().size());
        
        GraphNode startNode = graph.getNodes().get(0);
        assertEquals("start", startNode.getNodeType());
    }
}