package com.playerPlugin.playerTaskX.api.model.session;

import java.util.Map;

public class NextNodeResult {

    private final String nextNodeId;
    private final Map<String, Object> contextUpdate;
    private final boolean terminal;

    /**
     * 构造下一个节点结果
     * @param nextNodeId 下一个节点ID，null表示等待
     * @param contextUpdate 上下文更新
     * @param terminal 是否为终止节点
     */
    public NextNodeResult(String nextNodeId, Map<String, Object> contextUpdate, boolean terminal) {
        this.nextNodeId = nextNodeId;
        this.contextUpdate = contextUpdate != null ? contextUpdate : Map.of();
        this.terminal = terminal;
    }

    /**
     * 等待状态，表示当前节点处理未完成，需要继续等待
     * @return 等待结果
     */
    public static NextNodeResult waiting() {
        return new NextNodeResult(null, Map.of(), false);
    }

    /**
     * 进入下一个节点
     * @param nodeId 下一个节点ID
     * @return 下一个节点结果
     */
    public static NextNodeResult next(String nodeId) {
        return new NextNodeResult(nodeId, Map.of(), false);
    }

    /**
     * 进入下一个节点并更新上下文
     * @param nodeId 下一个节点ID
     * @param contextUpdate 上下文更新
     * @return 下一个节点结果
     */
    public static NextNodeResult next(String nodeId, Map<String, Object> contextUpdate) {
        return new NextNodeResult(nodeId, contextUpdate, false);
    }

    /**
     * 终止节点，表示任务流程结束
     * @param nodeId 终止的节点ID
     * @return 终止结果
     */
    public static NextNodeResult terminal(String nodeId) {
        return new NextNodeResult(nodeId, Map.of(), true);
    }

    /**
     * 获取下一个节点ID
     * @return 下一个节点ID，null表示等待
     */
    public String getNextNodeId() { return nextNodeId; }

    /**
     * 获取上下文更新
     * @return 上下文更新Map
     */
    public Map<String, Object> getContextUpdate() { return contextUpdate; }

    /**
     * 是否为终止节点
     * @return true表示任务流程终止
     */
    public boolean isTerminal() { return terminal; }
}