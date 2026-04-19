package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
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
        
        String taskType = getTaskType(graph);
        
        // 根据 taskType 处理重置逻辑
        handleTaskTypeReset(session, taskType);
        
        session.markNodeCompleted(session.getCurrentNodeId());
        
        // 执行 Completion 后续的 Action 节点
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(e -> e.getTargetId())
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        // 返回第一个后续节点（通常是 Action）
        String nextNodeId = nextNodes.get(0);
        return NextNodeResult.next(nextNodeId);
    }
    
    private String getTaskType(QuestGraph graph) {
        GraphNode taskNode = graph.getNodes().stream()
            .filter(n -> "task".equals(n.getNodeType()))
            .findFirst()
            .orElse(null);
        
        if (taskNode == null) return "FOREVER";
        
        Map<String, Object> data = taskNode.getData();
        if (data == null) return "FOREVER";
        
        Object taskType = data.get("taskType");
        return taskType != null ? taskType.toString() : "FOREVER";
    }
    
    private void handleTaskTypeReset(QuestSession session, String taskType) {
        switch (taskType) {
            case "CYCLE", "TIMER" -> {
                // 重置任务状态，保留进度
                session.resetProgress();
            }
            case "FOREVER" -> {
                // 任务保持完成状态
                session.setStatus(PTXTaskStatus.COMPLETED);
            }
            case "LIMIT" -> {
                // 任务结束，不再可接取
                session.setStatus(PTXTaskStatus.CLAIMED);
            }
            case "NONE" -> {
                // 无特殊逻辑
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
