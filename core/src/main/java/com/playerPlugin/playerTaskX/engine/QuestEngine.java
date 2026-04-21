package com.playerPlugin.playerTaskX.engine;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.handler.NodeHandlerRegistry;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.api.service.SessionStorage;
import com.playerPlugin.playerTaskX.engine.exception.GraphExecutionException;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class QuestEngine {
    private static final Logger log = LoggerFactory.getLogger(QuestEngine.class);
    
    private final QuestSessionManager sessionManager;
    private final NodeHandlerRegistry handlerRegistry;
    private final TaskManager taskManager;
    private final SessionStorage sessionStorage;
    
    /**
     * 构造任务引擎
     * @param sessionManager 会话管理器
     * @param handlerRegistry 节点处理器注册表
     * @param taskManager 任务管理器
     * @param sessionStorage 会话存储
     */
    public QuestEngine(QuestSessionManager sessionManager,
                       NodeHandlerRegistry handlerRegistry,
                       TaskManager taskManager,
                       SessionStorage sessionStorage) {
        this.sessionManager = sessionManager;
        this.handlerRegistry = handlerRegistry;
        this.taskManager = taskManager;
        this.sessionStorage = sessionStorage;
    }
    
    /**
     * 玩家开始任务，创建任务会话并初始化
     * @param player 玩家
     * @param task 任务定义（需包含任务图）
     */
    public void startQuest(Player player, TaskDefinition task) {
        QuestGraph graph = task.getGraph();
        if (graph == null || !task.hasGraph()) {
            throw new GraphExecutionException("Task " + task.getId() + " has no graph");
        }
        
        String startNodeId = findStartNode(graph);
        if (startNodeId == null) {
            throw new GraphExecutionException("No start node found in graph");
        }
        
        QuestSession session = new QuestSession(player.getUniqueId(), task.getId(), startNodeId);
        sessionManager.createSession(session);
        sessionStorage.save(session);
    }
    
    /**
     * 处理玩家触发的事件，转发给对应的任务会话处理
     * @param player 事件触发玩家
     * @param event 触发的事件
     */
    public void handleEvent(Player player, Event event) {
        Collection<QuestSession> sessions = sessionManager.getPlayerSessions(player.getUniqueId());
        
        for (QuestSession session : sessions) {
            if (session.getStatus() != PTXTaskStatus.IN_PROGRESS) continue;
            
            TaskDefinition task = taskManager.getTask(session.getQuestId()).orElse(null);
            if (task == null || !task.hasGraph()) continue;
            
            QuestGraph graph = task.getGraph();
            String currentNodeId = session.getCurrentNodeId();
            
            GraphNode currentNode = findNode(graph, currentNodeId);
            if (currentNode == null) {
                log.warn("Session {} has invalid current node {}", session, currentNodeId);
                continue;
            }
            
            NodeHandler handler = handlerRegistry.getHandler(currentNode.getNodeType());
            if (handler == null) {
                log.warn("No handler for node type: {}", currentNode.getNodeType());
                continue;
            }
            
            NextNodeResult result = handler.execute(session, event, graph);
            
            if (result.getNextNodeId() != null) {
                session.setCurrentNodeId(result.getNextNodeId());
                session.updateContext(result.getContextUpdate());
                
                if (result.isTerminal()) {
                    completeQuest(session, task);
                }
                
                sessionStorage.save(session);
            }
        }
    }
    
    /**
     * 玩家放弃任务
     * @param player 玩家
     * @param questId 任务ID
     */
    public void abandonQuest(Player player, String questId) {
        sessionManager.getSession(player.getUniqueId(), questId)
            .ifPresent(session -> {
                session.setStatus(PTXTaskStatus.ABANDONED);
                sessionStorage.save(session);
                sessionManager.removeSession(player.getUniqueId(), questId);
            });
    }
    
    /** 从存储恢复所有活跃会话到内存 */
    public void restoreSessions() {
        Collection<QuestSession> activeSessions = sessionStorage.findAllActive();
        for (QuestSession session : activeSessions) {
            sessionManager.createSession(session);
        }
    }
    
    /** 完成任务，设置状态为已完成并标记当前节点完成 */
    private void completeQuest(QuestSession session, TaskDefinition task) {
        session.setStatus(PTXTaskStatus.COMPLETED);
        session.markNodeCompleted(session.getCurrentNodeId());
    }
    
    /** 在任务图中查找起始节点（nodeType为"start"） */
    private String findStartNode(QuestGraph graph) {
        return graph.getNodes().stream()
            .filter(n -> "start".equals(n.getNodeType()))
            .map(GraphNode::getId)
            .findFirst()
            .orElseGet(() -> graph.getNodes().isEmpty() ? null : graph.getNodes().get(0).getId());
    }
    
    /** 根据节点ID在任务图中查找节点 */
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    /** 获取从指定节点出发的所有边指向的节点 */
    protected List<GraphNode> getOutgoingEdges(QuestGraph graph, String nodeId) {
        return graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(nodeId))
            .map(e -> findNode(graph, e.getTargetId()))
            .filter(Objects::nonNull)
            .toList();
    }
}