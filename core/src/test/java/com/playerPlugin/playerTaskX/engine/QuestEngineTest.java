package com.playerPlugin.playerTaskX.engine;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskType;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuestEngineTest {
    
    @Test
    void testStartQuestWithGraph() {
        TaskDefinition task = createTestTask();
        
        assertTrue(task.hasGraph());
        assertNotNull(task.getGraph());
        assertEquals(2, task.getGraph().getNodes().size());
    }
    
    @Test
    void testQuestGraphHasEdges() {
        TaskDefinition task = createTestTask();
        QuestGraph graph = task.getGraph();
        
        assertEquals(1, graph.getEdges().size());
        assertEquals("start1", graph.getEdges().get(0).getSourceId());
        assertEquals("task1", graph.getEdges().get(0).getTargetId());
    }
    
    @Test
    void testTaskDefinitionWithGraphNotNull() {
        List<GraphNode> nodes = List.of(
            new GraphNode("n1", "start", 0, 0, Map.of())
        );
        List<NodeConnection> edges = List.of();
        QuestGraph graph = new QuestGraph("g1", "Graph", nodes, edges);
        
        TaskDefinition task = new TaskDefinition("t1", "Test", "desc", null, PTXTaskType.FOREVER, 
            List.of(), List.of(), graph);
        
        assertTrue(task.hasGraph());
        assertEquals("g1", task.getGraph().getId());
    }
    
    @Test
    void testTaskDefinitionWithoutGraph() {
        TaskDefinition task = new TaskDefinition("t1", "Test", "desc", null, PTXTaskType.FOREVER, 
            List.of(), List.of(), null);
        
        assertFalse(task.hasGraph());
    }
    
    private TaskDefinition createTestTask() {
        List<GraphNode> nodes = List.of(
            new GraphNode("start1", "start", 0, 0, Map.of()),
            new GraphNode("task1", "task", 100, 0, Map.of())
        );
        List<NodeConnection> edges = List.of(
            new NodeConnection("e1", "start1", "task1", null)
        );
        QuestGraph graph = new QuestGraph("q1", "Test", nodes, edges);
        
        return new TaskDefinition("q1", "Test Quest", "desc", null, PTXTaskType.FOREVER, 
            List.of(), List.of(), graph);
    }
}