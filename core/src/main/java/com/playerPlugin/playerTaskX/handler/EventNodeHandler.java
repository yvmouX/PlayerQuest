package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.bukkit.event.Event;

public class EventNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "event"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        return NextNodeResult.waiting();
    }
    
    @Override
    public boolean canHandle(String nodeType) { return "event".equals(nodeType); }
}