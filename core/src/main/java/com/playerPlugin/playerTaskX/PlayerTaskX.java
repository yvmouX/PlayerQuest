package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.YLib;
import cn.yvmou.ylib.logger.Logger;
import com.playerPlugin.playerTaskX.api.handler.NodeHandlerRegistry;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;
import com.playerPlugin.playerTaskX.api.service.SessionStorage;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;
import com.playerPlugin.playerTaskX.command.TaskAdminCommand;
import com.playerPlugin.playerTaskX.command.TaskCommand;
import com.playerPlugin.playerTaskX.configuration.EditorConfiguration;
import com.playerPlugin.playerTaskX.configuration.GeneralConfiguration;
import com.playerPlugin.playerTaskX.configuration.StorgeConfiguration;
import com.playerPlugin.playerTaskX.engine.QuestEngine;
import com.playerPlugin.playerTaskX.engine.QuestSessionManager;
import com.playerPlugin.playerTaskX.engine.handler.*;
import com.playerPlugin.playerTaskX.event.PlayerJoinHandler;
import com.playerPlugin.playerTaskX.listener.GraphEventListener;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import com.playerPlugin.playerTaskX.storage.StorageFactory;
import com.playerPlugin.playerTaskX.web.EditorServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class PlayerTaskX extends JavaPlugin {
    private TaskManager taskManager;
    private EditorServer editorServer;
    private QuestEngine questEngine;
    private QuestSessionManager sessionManager;
    private GeneralConfiguration generalConfig;
    private StorgeConfiguration storageConfig;
    private EditorConfiguration editorConfig;
    private TaskStorage taskStorage;
    private ProgressStorage progressStorage;
    private SessionStorage sessionStorage;

    public static Logger log;

    @Override
    public void onEnable() {
        // 初始化 YLib 和配置
        YLib ylib = YLib.init(this);
        log = ylib.getLogger();
        generalConfig = ylib.getConfigurationManager().registerConfiguration(GeneralConfiguration.class);
        storageConfig = ylib.getConfigurationManager().registerConfiguration(StorgeConfiguration.class);
        editorConfig = ylib.getConfigurationManager().registerConfiguration(EditorConfiguration.class);

        // 初始化存储
        File basePath = getDataFolder();
        File storageDir = new File(basePath, "data");
        String taskStorageType = storageConfig.getStorageType_TaskDefinition();
        String progressStorageType = storageConfig.getStorageType_PlayerProgress();
        String sessionStorageType = storageConfig.getStorageType_Session();

        taskStorage = StorageFactory.createTaskStorage(taskStorageType, storageDir, storageConfig);
        progressStorage = StorageFactory.createProgressStorage(progressStorageType, storageDir, storageConfig);
        sessionStorage = StorageFactory.createSessionStorage(sessionStorageType, storageDir, storageConfig);

        sessionManager = new QuestSessionManager();

        // 注册节点处理器
        NodeHandlerRegistry handlerRegistry = new NodeHandlerRegistry();
        registerHandlers(handlerRegistry);

        // 初始化任务引擎
        this.taskManager = new TaskManager(taskStorage, progressStorage);
        taskManager.loadTasks();

        questEngine = new QuestEngine(sessionManager, handlerRegistry, taskManager, sessionStorage);
        taskManager.setQuestEngine(questEngine);
        //questEngine.restoreSessions();

        // 注册命令和事件
        ylib.getCommandManager().register(new TaskCommand(taskManager));
        ylib.getCommandManager().register(new TaskAdminCommand(taskManager));

        getServer().getPluginManager().registerEvents(new PlayerJoinHandler(taskManager, questEngine), this);
        getServer().getPluginManager().registerEvents(new GraphEventListener(questEngine), this);

        // 启动编辑器服务
        int editorPort = editorConfig.getPort();
        this.editorServer = new EditorServer(taskManager, getDataFolder().toPath());
        editorServer.start(editorPort);

        log.info("PlayerTaskX enabled");
    }

    private void registerHandlers(NodeHandlerRegistry registry) {
        registry.register(new StartNodeHandler());
        registry.register(new TriggerNodeHandler());
        registry.register(new ObjectiveNodeHandler());
        registry.register(new ActionNodeHandler());
        registry.register(new CompletionNodeHandler());
    }

    @Override
    public void onDisable() {
        if (editorServer != null) {
            editorServer.stop();
        }
        log.info("PlayerTaskX disabled");
    }
}