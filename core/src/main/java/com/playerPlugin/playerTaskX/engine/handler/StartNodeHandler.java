package com.playerPlugin.playerTaskX.engine.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.engine.GraphHelper;
import org.bukkit.event.Event;

public class StartNodeHandler implements NodeHandler {
    private final GraphHelper graphHelper = new GraphHelper();

    @Override
    public String getNodeType() { return "start"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        session.markNodeCompleted(session.getCurrentNodeId());
        
        return graphHelper.getNextNodeResult(session.getCurrentNodeId(), graph, true);
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "start".equals(nodeType);
    }
}