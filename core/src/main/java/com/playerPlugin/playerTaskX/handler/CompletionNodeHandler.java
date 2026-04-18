package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class CompletionNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "completion"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.terminal(session.getCurrentNodeId());
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return NextNodeResult.terminal(session.getCurrentNodeId());
        
        Map<String, Object> data = currentNode.getData();
        if (data == null) {
            session.markNodeCompleted(session.getCurrentNodeId());
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        List<Map<String, Object>> rewardsData = (List<Map<String, Object>>) data.get("rewards");
        if (rewardsData != null) {
            for (Map<String, Object> rewardData : rewardsData) {
                grantReward(player, rewardData);
            }
        }
        
        session.markNodeCompleted(session.getCurrentNodeId());
        return NextNodeResult.terminal(session.getCurrentNodeId());
    }
    
    private void grantReward(Player player, Map<String, Object> rewardData) {
        String type = (String) rewardData.get("type");
        Object value = rewardData.get("value");
        
        switch (type) {
            case "item" -> {
                if (value instanceof String materialName) {
                    try {
                        org.bukkit.Material material = org.bukkit.Material.valueOf(materialName.toUpperCase());
                        int amount = ((Number) rewardData.getOrDefault("amount", 1)).intValue();
                        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(material, amount));
                    } catch (IllegalArgumentException e) {
                        org.slf4j.LoggerFactory.getLogger(CompletionNodeHandler.class)
                            .warn("Invalid material for reward: {}", value);
                    }
                }
            }
            case "xp" -> {
                if (value instanceof Number) {
                    player.giveExp(((Number) value).intValue());
                }
            }
            case "money" -> {
                org.slf4j.LoggerFactory.getLogger(CompletionNodeHandler.class)
                    .info("Money reward of {} pending economy plugin integration", value);
            }
            case "command" -> {
                if (value instanceof String command) {
                    if (command.startsWith("/")) command = command.substring(1);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                        command.replace("%player%", player.getName()));
                }
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
        return "completion".equals(nodeType);
    }
}