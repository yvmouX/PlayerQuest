package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class ConditionNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "condition"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.waiting();
        
        Map<String, Object> data = currentNode.getData();
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) data.get("conditions");
        
        boolean allMet = true;
        if (conditions != null) {
            for (Map<String, Object> cond : conditions) {
                String conditionType = (String) cond.get("conditionType");
                Map<String, Object> params = (Map<String, Object>) cond.get("params");
                if (!evaluateCondition(conditionType, params, session)) {
                    allMet = false;
                    break;
                }
            }
        }
        
        session.markNodeCompleted(session.getCurrentNodeId());
        
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        String targetNodeId = allMet ? nextNodes.get(0) : (nextNodes.size() > 1 ? nextNodes.get(1) : nextNodes.get(0));
        return NextNodeResult.next(targetNodeId, Map.of("conditionResult", allMet));
    }
    
    private boolean evaluateCondition(String type, Map<String, Object> params, QuestSession session) {
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return false;
        
        switch (type) {
            case "PERMISSION" -> {
                String permission = (String) params.get("permission");
                return permission != null && player.hasPermission(permission);
            }
            case "HAS_ITEM" -> {
                String itemId = (String) params.get("itemId");
                int count = ((Number) params.getOrDefault("count", 1)).intValue();
                org.bukkit.Material material = org.bukkit.Material.valueOf(
                    params.getOrDefault("material", "DIAMOND").toString().toUpperCase());
                return player.getInventory().containsAtLeast(new org.bukkit.inventory.ItemStack(material), count);
            }
            case "KILL_MOB" -> {
                String mobType = (String) params.get("mobType");
                int killCount = ((Number) session.getContext().getOrDefault("kills_" + mobType, 0)).intValue();
                int required = ((Number) params.getOrDefault("count", 1)).intValue();
                return killCount >= required;
            }
            case "COLLECT_ITEM" -> {
                String itemId = (String) params.get("itemId");
                int collected = ((Number) session.getContext().getOrDefault("collected_" + itemId, 0)).intValue();
                int required = ((Number) params.getOrDefault("count", 1)).intValue();
                return collected >= required;
            }
            case "PLAYER_LEVEL" -> {
                int level = ((Number) params.getOrDefault("level", 1)).intValue();
                String operator = (String) params.getOrDefault("operator", "GTE");
                return switch (operator) {
                    case "EQ" -> player.getLevel() == level;
                    case "GT" -> player.getLevel() > level;
                    case "GTE" -> player.getLevel() >= level;
                    case "LT" -> player.getLevel() < level;
                    case "LTE" -> player.getLevel() <= level;
                    default -> false;
                };
            }
            case "TIME_RANGE" -> {
                int currentHour = java.time.LocalTime.now().getHour();
                int startHour = ((Number) params.getOrDefault("startHour", 0)).intValue();
                int endHour = ((Number) params.getOrDefault("endHour", 24)).intValue();
                if (startHour <= endHour) {
                    return currentHour >= startHour && currentHour < endHour;
                } else {
                    return currentHour >= startHour || currentHour < endHour;
                }
            }
            case "IN_REGION" -> {
                String region = (String) params.get("region");
                return Boolean.TRUE.equals(session.getContext().get("in_region_" + region));
            }
            default -> {
                org.slf4j.LoggerFactory.getLogger(ConditionNodeHandler.class).warn("Unknown condition type: {}", type);
                return false;
            }
        }
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "condition".equals(nodeType);
    }
}