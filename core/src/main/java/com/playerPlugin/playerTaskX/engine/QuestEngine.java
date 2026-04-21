package com.playerPlugin.playerTaskX.engine;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.handler.NodeHandlerRegistry;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.api.model.template.ObjectiveTemplate;
import com.playerPlugin.playerTaskX.api.service.SessionStorage;
import com.playerPlugin.playerTaskX.api.exception.GraphExecutionException;
import com.playerPlugin.playerTaskX.api.service.TemplateService;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Collection;
import java.util.Map;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;

public class QuestEngine {
    private final QuestSessionManager sessionManager;
    private final NodeHandlerRegistry handlerRegistry;
    private final TaskManager taskManager;
    private final SessionStorage sessionStorage;
    private final GraphHelper helper;
    
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
        this.helper = new GraphHelper();
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
        
        String startNodeId = helper.findStartNode(graph);
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
            if (session.getStatus() != PTXTaskStatus.IN_PROGRESS) continue; // 如果任务状态不是 IN_PROGRESS --跳过
            
            TaskDefinition task = taskManager.getTask(session.getQuestId()).orElse(null);
            if (task == null || !task.hasGraph()) continue;
            
            QuestGraph graph = task.getGraph();
            String currentNodeId = session.getCurrentNodeId();
            
            GraphNode currentNode = helper.findNode(graph, currentNodeId);
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
            
            if (result.getNextNodeId() != null) { // 如果下一个节点不处于 waiting 状态 前进到下一个节点
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
     * 根据事件更新玩家进度
     * @param player 玩家
     * @param event 触发的事件
     */
    public void updateProgress(Player player, Event event) {
        Collection<QuestSession> sessions = sessionManager.getPlayerSessions(player.getUniqueId());
        
        for (QuestSession session : sessions) {
            if (session.getStatus() != PTXTaskStatus.IN_PROGRESS) continue;
            
            TaskDefinition task = taskManager.getTask(session.getQuestId()).orElse(null);
            if (task == null || !task.hasGraph()) continue;
            
            QuestGraph graph = task.getGraph();
            GraphNode currentNode = helper.findNode(graph, session.getCurrentNodeId());
            if (currentNode == null || !"objective".equals(currentNode.getNodeType())) continue;
            
            Map<String, Object> updates = calculateProgressUpdate(event, player, currentNode, session);
            if (!updates.isEmpty()) {
                session.updateContext(updates);
                sessionStorage.save(session);
            }
        }
    }
    
    /**
     * 根据事件类型和当前节点配置计算需要更新的进度
     */
    private Map<String, Object> calculateProgressUpdate(Event event, Player player, GraphNode node, QuestSession session) {
        Map<String, Object> data = node.getData();
        String nodeType = (String) data.get("type");
        String objectiveType = null;
        String templateID = null;
        ObjectiveTemplate template = null;

        if (nodeType == null) return Map.of();

        if (nodeType.equals("objective")) { // 必须匹配 objective Node ，只有它包含templateId这个字段
            templateID = (String) data.get("templateId");
            template = TemplateService.getObjectiveTemplate(templateID);
            if (template == null) return Map.of();

            objectiveType = template.getType();
        }

        if (objectiveType == null) return Map.of();
        if (templateID == null) return Map.of();
        
        if (event instanceof BlockBreakEvent breakEvent) {
            if ("break_block".equals(objectiveType)) {
                String targetBlock = (String) template.getDefaultConfig().get("target");
                String brokenBlock = breakEvent.getBlock().getType().name();
                if (targetBlock != null && targetBlock.equalsIgnoreCase(brokenBlock)) {
                    int current = getSessionValue(session, "broken_" + brokenBlock, 0);
                    return Map.of("broken_" + brokenBlock, current + 1);
                }
            }
        }
        
        return Map.of();
    }

    private int getSessionValue(QuestSession session, String key, int defaultValue) {
        Object value = session.getContext().get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
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
    
    /** 从存储恢复指定玩家的活跃会话到内存 */
    public void restorePlayerSession(Player player) {
        Collection<QuestSession> activeSessions = sessionStorage.findAllActive();
        for (QuestSession session : activeSessions) {
            if (session.getPlayerId().equals(player.getUniqueId())) {
                sessionManager.createSession(session);
            };
        }
    }
    
    /** 完成任务，设置状态为已完成并标记当前节点完成 */
    private void completeQuest(QuestSession session, TaskDefinition task) {
        session.setStatus(PTXTaskStatus.COMPLETED);
        session.markNodeCompleted(session.getCurrentNodeId());
    }
    
}