package com.playerPlugin.playerTaskX.api.model.session;

import java.util.Map;

public class NextNodeResult {
    private final String nextNodeId;
    private final Map<String, Object> contextUpdate;
    private final boolean terminal;
    
    public NextNodeResult(String nextNodeId, Map<String, Object> contextUpdate, boolean terminal) {
        this.nextNodeId = nextNodeId;
        this.contextUpdate = contextUpdate != null ? contextUpdate : Map.of();
        this.terminal = terminal;
    }
    
    public static NextNodeResult waiting() {
        return new NextNodeResult(null, Map.of(), false);
    }
    
    public static NextNodeResult next(String nodeId) {
        return new NextNodeResult(nodeId, Map.of(), false);
    }
    
    public static NextNodeResult next(String nodeId, Map<String, Object> contextUpdate) {
        return new NextNodeResult(nodeId, contextUpdate, false);
    }
    
    public static NextNodeResult terminal(String nodeId) {
        return new NextNodeResult(nodeId, Map.of(), true);
    }
    
    public String getNextNodeId() { return nextNodeId; }
    public Map<String, Object> getContextUpdate() { return contextUpdate; }
    public boolean isTerminal() { return terminal; }
}