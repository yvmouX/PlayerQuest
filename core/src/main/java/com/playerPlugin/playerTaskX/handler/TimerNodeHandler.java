package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class TimerNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "timer"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.waiting();
        
        Map<String, Object> data = currentNode.getData();
        String timerType = (String) data.getOrDefault("timerType", "DELAY");
        
        switch (timerType) {
            case "DELAY" -> {
                long delaySeconds = ((Number) data.getOrDefault("delaySeconds", 60)).longValue();
                String timerKey = "timer_start_" + session.getCurrentNodeId();
                long startTime = ((Number) session.getContext().getOrDefault(timerKey, 0)).longValue();
                if (startTime == 0) {
                    session.updateContext(Map.of(timerKey, System.currentTimeMillis()));
                } else if (System.currentTimeMillis() - startTime >= delaySeconds * 1000) {
                    session.markNodeCompleted(session.getCurrentNodeId());
                    return getNextNode(graph, session.getCurrentNodeId());
                }
            }
            case "COOLDOWN" -> {
                long cooldownSeconds = ((Number) data.getOrDefault("cooldownSeconds", 300)).longValue();
                String cooldownKey = "cooldown_end_" + session.getCurrentNodeId();
                Long cooldownEnd = ((Number) session.getContext().getOrDefault(cooldownKey, 0)).longValue();
                long now = System.currentTimeMillis();
                if (cooldownEnd == 0) {
                    session.updateContext(Map.of(cooldownKey, now + cooldownSeconds * 1000));
                } else if (now >= cooldownEnd) {
                    session.updateContext(Map.of(cooldownKey, now + cooldownSeconds * 1000));
                    session.markNodeCompleted(session.getCurrentNodeId());
                    return getNextNode(graph, session.getCurrentNodeId());
                }
            }
            case "INTERVAL" -> {
                long intervalSeconds = ((Number) data.getOrDefault("intervalSeconds", 60)).longValue();
                int maxReps = ((Number) data.getOrDefault("repeatCount", 1)).intValue();
                String intervalKey = "interval_" + session.getCurrentNodeId();
                Map<String, Object> intervalData = (Map<String, Object>) session.getContext().get(intervalKey);
                long lastFire = intervalData != null ? ((Number) intervalData.getOrDefault("lastFire", 0)).longValue() : 0;
                int reps = intervalData != null ? ((Number) intervalData.getOrDefault("reps", 0)).intValue() : 0;
                long now = System.currentTimeMillis();
                if (lastFire == 0 || now - lastFire >= intervalSeconds * 1000) {
                    reps++;
                    session.updateContext(Map.of(intervalKey, Map.of("lastFire", now, "reps", reps)));
                    if (reps >= maxReps) {
                        session.markNodeCompleted(session.getCurrentNodeId());
                        return getNextNode(graph, session.getCurrentNodeId());
                    }
                }
            }
        }
        
        return NextNodeResult.waiting();
    }
    
    private NextNodeResult getNextNode(QuestGraph graph, String currentNodeId) {
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(currentNodeId))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(currentNodeId);
        }
        return NextNodeResult.next(nextNodes.get(0));
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public boolean canHandle(String nodeType) { return "timer".equals(nodeType); }
}