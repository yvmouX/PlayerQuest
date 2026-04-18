package com.playerPlugin.playerTaskX.engine;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.handler.NodeHandlerRegistry;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.engine.exception.GraphExecutionException;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import com.playerPlugin.playerTaskX.storage.SessionStorage;
import org.bukkit.Bukkit;
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
    
    public QuestEngine(QuestSessionManager sessionManager,
                       NodeHandlerRegistry handlerRegistry,
                       TaskManager taskManager,
                       SessionStorage sessionStorage) {
        this.sessionManager = sessionManager;
        this.handlerRegistry = handlerRegistry;
        this.taskManager = taskManager;
        this.sessionStorage = sessionStorage;
    }
    
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
    
    public void abandonQuest(Player player, String questId) {
        sessionManager.getSession(player.getUniqueId(), questId)
            .ifPresent(session -> {
                session.setStatus(PTXTaskStatus.ABANDONED);
                sessionStorage.save(session);
                sessionManager.removeSession(player.getUniqueId(), questId);
            });
    }
    
    public void restoreSessions() {
        Collection<QuestSession> activeSessions = sessionStorage.findAllActive();
        for (QuestSession session : activeSessions) {
            sessionManager.createSession(session);
        }
    }
    
    private void completeQuest(QuestSession session, TaskDefinition task) {
        session.setStatus(PTXTaskStatus.COMPLETED);
        session.markNodeCompleted(session.getCurrentNodeId());
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) {
            log.warn("Cannot grant rewards - player {} not online", session.getPlayerId());
            return;
        }
        
        for (var reward : task.getRewards()) {
            reward.grant(player);
        }
    }
    
    private String findStartNode(QuestGraph graph) {
        return graph.getNodes().stream()
            .filter(n -> "start".equals(n.getNodeType()))
            .map(GraphNode::getId)
            .findFirst()
            .orElseGet(() -> graph.getNodes().isEmpty() ? null : graph.getNodes().get(0).getId());
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    protected List<GraphNode> getOutgoingEdges(QuestGraph graph, String nodeId) {
        return graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(nodeId))
            .map(e -> findNode(graph, e.getTargetId()))
            .filter(Objects::nonNull)
            .toList();
    }
}