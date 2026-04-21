package com.playerPlugin.playerTaskX.engine;

import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;

import java.util.List;
import java.util.Objects;

public class GraphHelper {

    /**
     * 在任务图中查找起始节点
     * @param graph 任务图
     * @return 起始节点ID，若无则返回第一个节点，若图为空则返回null
     */
    public String findStartNode(QuestGraph graph) {
        return graph.getNodes().stream()
                .filter(n -> "start".equals(n.getNodeType()))
                .map(GraphNode::getId)
                .findFirst()
                .orElseGet(() -> graph.getNodes().isEmpty() ? null : graph.getNodes().getFirst().getId());
    }

    /**
     * 根据节点ID在任务图中查找节点
     * @param graph 任务图
     * @param nodeId 节点ID
     * @return 节点对象，若不存在则返回null
     */
    public GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取从指定节点出发的所有边指向的节点
     * @param graph 任务图
     * @param nodeId 源节点ID
     * @return 目标节点列表
     */
    public List<GraphNode> getOutgoingNodes(QuestGraph graph, String nodeId) {
        return graph.getEdges().stream()
                .filter(e -> e.getSourceId().equals(nodeId))
                .map(e -> findNode(graph, e.getTargetId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 获取下一个节点的结果
     * @param currentNodeId 当前节点ID
     * @param graph 任务图
     * @param waitingWhenEmpty 当没有下一个节点时返回 waiting 还是 terminal
     * @return NextNodeResult
     */
    public NextNodeResult getNextNodeResult(String currentNodeId, QuestGraph graph, boolean waitingWhenEmpty) {
        List<String> nextNodes = graph.getEdges().stream()
                .filter(e -> e.getSourceId().equals(currentNodeId))
                .map(NodeConnection::getTargetId)
                .toList();

        if (nextNodes.isEmpty()) {
            return waitingWhenEmpty ? NextNodeResult.waiting() : NextNodeResult.terminal(currentNodeId);
        }

        return NextNodeResult.next(nextNodes.getFirst());
    }
}
