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

public class TaskNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "task"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.waiting();
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return NextNodeResult.waiting();
        
        Map<String, Object> data = currentNode.getData();
        if (data == null) return NextNodeResult.waiting();
        
        // Task 节点不再处理 objectives，专注流程控制
        // 检查是否有连接的 Objective 节点
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            // 没有后续节点，标记任务完成
            session.markNodeCompleted(session.getCurrentNodeId());
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        // 找到第一个 Objective 或 Completion 节点
        for (String nextNodeId : nextNodes) {
            GraphNode nextNode = findNode(graph, nextNodeId);
            if (nextNode != null) {
                String nodeType = nextNode.getNodeType();
                if ("objective".equals(nodeType) || "completion".equals(nodeType)) {
                    session.markNodeCompleted(session.getCurrentNodeId());
                    return NextNodeResult.next(nextNodeId);
                }
            }
        }
        
        // 没有找到 objective 或 completion，等待
        return NextNodeResult.waiting();
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "task".equals(nodeType);
    }
}
