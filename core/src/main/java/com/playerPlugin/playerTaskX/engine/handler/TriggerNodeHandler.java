package com.playerPlugin.playerTaskX.engine.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.engine.GraphHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class TriggerNodeHandler implements NodeHandler {
    private final GraphHelper graphHelper = new GraphHelper();

    @Override
    public String getNodeType() { return "trigger"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = graphHelper.findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.waiting();
        
        Map<String, Object> data = currentNode.getData();
        String conditionType = (String) data.get("conditionType");
        Map<String, Object> conditionConfig = (Map<String, Object>) data.get("conditionConfig");
        
        boolean conditionMet = evaluateCondition(conditionType, conditionConfig, session);
        
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        // trigger 只有满足条件才能进入任务
        if (!conditionMet) {
            // 条件不满足，任务不能接取，结束流程
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        session.markNodeCompleted(session.getCurrentNodeId());
        return NextNodeResult.next(nextNodes.get(0));
    }
    
    private boolean evaluateCondition(String type, Map<String, Object> config, QuestSession session) {
        if (type == null) return true;
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return false;
        
        switch (type) {
            case "quest_complete" -> {
                String questId = (String) config.get("questId");
                return checkQuestCompleted(session, questId);
            }
            case "permission" -> {
                String permission = (String) config.get("permission");
                return permission != null && player.hasPermission(permission);
            }
            case "npc_interact" -> {
                String npcId = (String) config.get("npcId");
                return Boolean.TRUE.equals(session.getContext().get("npc_interact_" + npcId));
            }
            default -> {
                org.slf4j.LoggerFactory.getLogger(TriggerNodeHandler.class)
                    .warn("Unknown trigger condition type: {}", type);
                return true;
            }
        }
    }
    
    private boolean checkQuestCompleted(QuestSession session, String questId) {
        return false;
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "trigger".equals(nodeType);
    }
}
