package com.playerPlugin.playerTaskX.engine.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.ActionTemplate;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.api.service.TemplateService;
import com.playerPlugin.playerTaskX.engine.GraphHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.Map;

public class ActionNodeHandler implements NodeHandler {
    private final GraphHelper graphHelper = new GraphHelper();

    @Override
    public String getNodeType() { return "action"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = graphHelper.findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.waiting();
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return NextNodeResult.waiting();
        
        Map<String, Object> data = currentNode.getData();
        String templateId = (String) data.get("templateId");
        
        executeAction(player, templateId);
        
        session.markNodeCompleted(session.getCurrentNodeId());
        
        return graphHelper.getNextNodeResult(session.getCurrentNodeId(), graph, true);
    }
    
    private void executeAction(Player player, String templateId) {
        Map<String, Object> config = getTemplateConfig(templateId);
        if (config == null) return;
        
        String actionType = (String) config.get("type");
        if (actionType == null) return;
        
        switch (actionType) {
            case "give_item" -> {
                String itemId = (String) config.get("item");
                int amount = ((Number) config.getOrDefault("amount", 1)).intValue();
                try {
                    org.bukkit.Material material = org.bukkit.Material.valueOf(itemId.toUpperCase());
                    player.getInventory().addItem(new org.bukkit.inventory.ItemStack(material, amount));
                } catch (IllegalArgumentException e) {
                    org.slf4j.LoggerFactory.getLogger(ActionNodeHandler.class)
                        .warn("Invalid material: {}", itemId);
                }
            }
            case "execute_command" -> {
                String command = (String) config.get("command");
                if (command != null) {
                    if (command.startsWith("/")) command = command.substring(1);
                    command = command.replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            }
            case "send_message" -> {
                String message = (String) config.get("message");
                if (message != null) {
                    player.sendMessage(message);
                }
            }
            case "play_effect" -> {
                String effect = (String) config.get("effect");
                if (effect != null) {
                    player.getWorld().playEffect(player.getLocation(), 
                        org.bukkit.Effect.valueOf(effect.toUpperCase()), 1);
                }
            }
            case "sound" -> {
                String sound = (String) config.get("sound");
                float volume = ((Number) config.getOrDefault("volume", 1.0f)).floatValue();
                float pitch = ((Number) config.getOrDefault("pitch", 1.0f)).floatValue();
                if (sound != null) {
                    player.playSound(player.getLocation(), sound, volume, pitch);
                }
            }
            case "give_xp" -> {
                int xp = ((Number) config.getOrDefault("xp", 0)).intValue();
                player.giveExp(xp);
            }
            default -> {
                org.slf4j.LoggerFactory.getLogger(ActionNodeHandler.class)
                    .warn("Unknown action type: {}", actionType);
            }
        }
    }
    
    private Map<String, Object> getTemplateConfig(String templateId) {
        ActionTemplate template = TemplateService.getActionTemplate(templateId);
        if (template == null) {
            org.slf4j.LoggerFactory.getLogger(ActionNodeHandler.class)
                .warn("Action template not found: {}", templateId);
            return null;
        }
        return template.getDefaultConfig();
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "action".equals(nodeType);
    }
}
