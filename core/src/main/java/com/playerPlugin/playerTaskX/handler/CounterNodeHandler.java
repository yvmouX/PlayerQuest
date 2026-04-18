package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class CounterNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "counter"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.waiting();
        
        Map<String, Object> data = currentNode.getData();
        String counterName = (String) data.getOrDefault("name", "default");
        int delta = event != null ? 1 : 0;
        
        int currentCount = ((Number) session.getContext().getOrDefault("counter_" + counterName, 0)).intValue();
        int newCount = currentCount + delta;
        
        session.updateContext(Map.of("counter_" + counterName, newCount));
        
        int threshold = ((Number) data.getOrDefault("threshold", Integer.MAX_VALUE)).intValue();
        if (newCount >= threshold) {
            session.markNodeCompleted(session.getCurrentNodeId());
            return getNextNode(graph, session.getCurrentNodeId());
        }
        
        return NextNodeResult.waiting();
    }
    
    private NextNodeResult getNextNode(QuestGraph graph, String currentNodeId) {
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(currentNodeId))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(currentNodeId);
        }
        return NextNodeResult.next(nextNodes.get(0));
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public boolean canHandle(String nodeType) { return "counter".equals(nodeType); }
}