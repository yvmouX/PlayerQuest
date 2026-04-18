package com.playerPlugin.playerTaskX.api.model.session;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import java.util.*;

public class QuestSession {
    private final UUID playerId;
    private final String questId;
    private String currentNodeId;
    private final Set<String> completedNodes;
    private final Map<String, Object> context;
    private final long startTime;
    private long lastActiveTime;
    private PTXTaskStatus status;
    
    public QuestSession(UUID playerId, String questId, String currentNodeId) {
        this.playerId = playerId;
        this.questId = questId;
        this.currentNodeId = currentNodeId;
        this.completedNodes = new HashSet<>();
        this.context = new HashMap<>();
        this.startTime = System.currentTimeMillis();
        this.lastActiveTime = startTime;
        this.status = PTXTaskStatus.IN_PROGRESS;
    }
    
    public UUID getPlayerId() { return playerId; }
    public String getQuestId() { return questId; }
    public String getCurrentNodeId() { return currentNodeId; }
    public Set<String> getCompletedNodes() { return Collections.unmodifiableSet(completedNodes); }
    public Map<String, Object> getContext() { return Collections.unmodifiableMap(context); }
    public long getStartTime() { return startTime; }
    public long getLastActiveTime() { return lastActiveTime; }
    public PTXTaskStatus getStatus() { return status; }
    
    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
        this.lastActiveTime = System.currentTimeMillis();
    }
    
    public void markNodeCompleted(String nodeId) {
        this.completedNodes.add(nodeId);
        this.lastActiveTime = System.currentTimeMillis();
    }
    
    public void updateContext(Map<String, Object> updates) {
        this.context.putAll(updates);
        this.lastActiveTime = System.currentTimeMillis();
    }
    
    public void setStatus(PTXTaskStatus status) {
        this.status = status;
    }
    
    public void setLastActiveTime(long lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }
}