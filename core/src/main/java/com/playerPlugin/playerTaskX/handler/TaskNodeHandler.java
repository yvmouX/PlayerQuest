package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.objective.Objective;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class TaskNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "task"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.waiting();
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return NextNodeResult.waiting();
        
        Map<String, Object> data = currentNode.getData();
        if (data == null) return NextNodeResult.waiting();
        
        List<Objective> objectives = (List<Objective>) data.get("objectives");
        if (objectives == null || objectives.isEmpty()) {
            session.markNodeCompleted(session.getCurrentNodeId());
            return getNextNode(graph, session.getCurrentNodeId());
        }
        
        boolean allComplete = true;
        for (Objective obj : objectives) {
            if (!obj.isCompleted(player)) {
                allComplete = false;
                if (obj.matchesEvent(event)) {
                    obj.applyProgress(player, 1);
                }
            }
        }
        
        if (allComplete) {
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
    public boolean canHandle(String nodeType) {
        return "task".equals(nodeType);
    }
}