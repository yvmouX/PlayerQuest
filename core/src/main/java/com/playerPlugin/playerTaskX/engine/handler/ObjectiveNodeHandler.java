package com.playerPlugin.playerTaskX.engine.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.ObjectiveTemplate;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.api.service.TemplateService;
import com.playerPlugin.playerTaskX.engine.GraphHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class ObjectiveNodeHandler implements NodeHandler {
    private final GraphHelper graphHelper = new GraphHelper();

    @Override
    public String getNodeType() { return "objective"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = graphHelper.findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.waiting();
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return NextNodeResult.waiting();
        
        Map<String, Object> data = currentNode.getData();
        String templateId = (String) data.get("templateId");
        
        boolean completed = checkObjectiveCompleted(player, session, templateId, event);
        
        if (!completed) {
            return NextNodeResult.waiting();
        }
        
        session.markNodeCompleted(session.getCurrentNodeId());
        
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.waiting();
        }
        
        String nextNodeId = nextNodes.get(0);
        return NextNodeResult.next(nextNodeId);
    }
    
    private boolean checkObjectiveCompleted(Player player, QuestSession session, 
            String templateId, Event event) {
        
        Map<String, Object> config = getTemplateConfig(templateId);
        if (config == null) {
            org.slf4j.LoggerFactory.getLogger(ObjectiveNodeHandler.class)
                .warn("Template not found for objective: {}", templateId);
            return false;
        }
        
        String objectiveType = (String) config.get("type");
        if (objectiveType == null) return true;
        
        switch (objectiveType) {
            case "kill_mob" -> {
                String mobType = (String) config.get("target");
                int required = ((Number) config.getOrDefault("amount", 1)).intValue();
                int current = ((Number) session.getContext().getOrDefault("kills_" + mobType, 0)).intValue();
                return current >= required;
            }
            case "collect_item" -> {
                String itemId = (String) config.get("target");
                int required = ((Number) config.getOrDefault("amount", 1)).intValue();
                int current = ((Number) session.getContext().getOrDefault("collected_" + itemId, 0)).intValue();
                return current >= required;
            }
            case "break_block" -> {
                String blockType = (String) config.get("target");
                int required = ((Number) config.getOrDefault("amount", 1)).intValue();
                int current = ((Number) session.getContext().getOrDefault("broken_" + blockType, 0)).intValue();
                return current >= required;
            }
            case "talk_to_npc" -> {
                String npcId = (String) config.get("target");
                return Boolean.TRUE.equals(session.getContext().get("npc_talked_" + npcId));
            }
            case "reach_location" -> {
                Map<String, Object> location = (Map<String, Object>) config.get("location");
                if (location == null) return true;
                return Boolean.TRUE.equals(session.getContext().get("reached_location"));
            }
            default -> {
                org.slf4j.LoggerFactory.getLogger(ObjectiveNodeHandler.class)
                    .warn("Unknown objective type: {}", objectiveType);
                return true;
            }
        }
    }
    
    private Map<String, Object> getTemplateConfig(String templateId) {
        ObjectiveTemplate template = TemplateService.getObjectiveTemplate(templateId);
        if (template == null) {
            org.slf4j.LoggerFactory.getLogger(ObjectiveNodeHandler.class)
                .warn("Objective template not found: {}", templateId);
            return null;
        }
        return template.getDefaultConfig();
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "objective".equals(nodeType);
    }
}
