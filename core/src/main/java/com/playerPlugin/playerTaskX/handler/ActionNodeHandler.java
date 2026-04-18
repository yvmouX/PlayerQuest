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

public class ActionNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "action"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.waiting();
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return NextNodeResult.waiting();
        
        Map<String, Object> data = currentNode.getData();
        String actionType = (String) data.get("actionType");
        Map<String, Object> actionParams = (Map<String, Object>) data.get("actionParams");
        
        executeAction(player, actionType, actionParams);
        
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
    
    private void executeAction(Player player, String actionType, Map<String, Object> params) {
        if (params == null) params = Map.of();
        switch (actionType) {
            case "GIVE_ITEM" -> {
                String materialName = (String) params.getOrDefault("material", "DIAMOND");
                int amount = ((Number) params.getOrDefault("amount", 1)).intValue();
                org.bukkit.Material material = org.bukkit.Material.valueOf(materialName.toUpperCase());
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material, amount);
                player.getInventory().addItem(item);
            }
            case "TAKE_ITEM" -> {
                String materialName = (String) params.getOrDefault("material", "DIAMOND");
                int amount = ((Number) params.getOrDefault("amount", 1)).intValue();
                org.bukkit.Material material = org.bukkit.Material.valueOf(materialName.toUpperCase());
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material, amount);
                player.getInventory().removeItem(item);
            }
            case "GIVE_MONEY" -> {
                double amount = ((Number) params.getOrDefault("amount", 0)).doubleValue();
            }
            case "TAKE_MONEY" -> {
                double amount = ((Number) params.getOrDefault("amount", 0)).doubleValue();
            }
            case "GIVE_XP" -> {
                int amount = ((Number) params.getOrDefault("amount", 0)).intValue();
                player.giveExp(amount);
            }
            case "SEND_MESSAGE" -> {
                String message = (String) params.getOrDefault("message", "");
                player.sendMessage(message);
            }
            case "BROADCAST" -> {
                String message = (String) params.getOrDefault("message", "");
                Bukkit.broadcastMessage(message);
            }
            case "EXECUTE_COMMAND" -> {
                String command = (String) params.getOrDefault("command", "");
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
            }
            case "PLAY_SOUND" -> {
                String soundName = (String) params.getOrDefault("sound", "ENTITY_PLAYER_LEVELUP");
                float volume = ((Number) params.getOrDefault("volume", 1.0f)).floatValue();
                float pitch = ((Number) params.getOrDefault("pitch", 1.0f)).floatValue();
                player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundName.toUpperCase()), volume, pitch);
            }
            default -> {
                org.slf4j.LoggerFactory.getLogger(ActionNodeHandler.class).warn("Unknown action type: {}", actionType);
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
        return "action".equals(nodeType);
    }
}