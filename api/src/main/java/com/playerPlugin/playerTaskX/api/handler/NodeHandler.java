package com.playerPlugin.playerTaskX.api.handler;

import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.bukkit.event.Event;

public interface NodeHandler {
    String getNodeType();
    
    NextNodeResult execute(QuestSession session, Event event, QuestGraph graph);
    
    boolean canHandle(String nodeType);
}
