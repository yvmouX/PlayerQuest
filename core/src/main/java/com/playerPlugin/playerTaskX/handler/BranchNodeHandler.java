package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.bukkit.event.Event;

import java.util.List;

public class BranchNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "branch"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        session.markNodeCompleted(session.getCurrentNodeId());
        
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        return NextNodeResult.next(nextNodes.get(0));
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "branch".equals(nodeType);
    }
}