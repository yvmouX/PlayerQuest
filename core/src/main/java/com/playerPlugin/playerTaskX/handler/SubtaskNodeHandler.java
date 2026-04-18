package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.engine.QuestSessionManager;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SubtaskNodeHandler implements NodeHandler {
    private final QuestSessionManager sessionManager;
    
    public SubtaskNodeHandler(QuestSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    
    @Override
    public String getNodeType() { return "subtask"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.waiting();
        
        Map<String, Object> data = currentNode.getData();
        String subtaskId = (String) data.getOrDefault("subtaskId", "");
        String subSessionKey = "subtask_session_" + subtaskId;
        
        String subSessionId = (String) session.getContext().get(subSessionKey);
        
        if (subSessionId == null) {
            QuestSession subSession = new QuestSession(session.getPlayerId(), subtaskId, "start");
            sessionManager.createSession(subSession);
            session.updateContext(Map.of(subSessionKey, subtaskId));
            return NextNodeResult.waiting();
        }
        
        Optional<QuestSession> subSessionOpt = sessionManager.getSession(session.getPlayerId(), subtaskId);
        if (subSessionOpt.isEmpty()) {
            session.markNodeCompleted(session.getCurrentNodeId());
            return getNextNode(graph, session.getCurrentNodeId());
        }
        
        QuestSession subSession = subSessionOpt.get();
        if (subSession.getStatus() == PTXTaskStatus.COMPLETED || subSession.getStatus() == PTXTaskStatus.CLAIMED) {
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
    public boolean canHandle(String nodeType) { return "subtask".equals(nodeType); }
}