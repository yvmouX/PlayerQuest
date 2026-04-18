package com.playerPlugin.playerTaskX.api.handler;

import java.util.*;

public class NodeHandlerRegistry {
    private final Map<String, NodeHandler> handlers = new HashMap<>();
    
    public void register(NodeHandler handler) {
        handlers.put(handler.getNodeType(), handler);
    }
    
    public NodeHandler getHandler(String nodeType) {
        return handlers.get(nodeType);
    }
    
    public Collection<NodeHandler> getAllHandlers() {
        return handlers.values();
    }
}
