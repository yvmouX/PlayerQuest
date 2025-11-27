package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.YLib;
import cn.yvmou.ylib.api.scheduler.UniversalScheduler;
import cn.yvmou.ylib.tools.LoggerTools;
import com.playerPlugin.playerTaskX.UI.MainUI;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.commands.CommandRegister;
import com.playerPlugin.playerTaskX.common.Enum.PTXStorgeType;
import com.playerPlugin.playerTaskX.event.EventBus;
import com.playerPlugin.playerTaskX.event.custom.TaskProgressEvent;
import com.playerPlugin.playerTaskX.model.Task.TaskDefinition;
import com.playerPlugin.playerTaskX.event.PlayerJoinHandler;
import com.playerPlugin.playerTaskX.event.SimpleEventBus;
import com.playerPlugin.playerTaskX.event.listeners.KillListener;
import com.playerPlugin.playerTaskX.service.TaskService;
import com.playerPlugin.playerTaskX.storage.StorageFactory;
import com.playerPlugin.playerTaskX.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.storage.TaskRepository;
import com.playerPlugin.playerTaskX.utils.Metrics;
import com.playerPlugin.playerTaskX.utils.UpdateHelper;
import me.devnatan.inventoryframework.ViewFrame;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerTaskX extends JavaPlugin {
    private LoggerTools log;
    private UniversalScheduler scheduler;
    private Economy economy;
    private boolean isEconomyEnabled = true;
    private PlayerPointsAPI ppAPI;
    private boolean isPlayerPointsEnabled = true;
    private ViewFrame viewFrame;

    private final PTXStorgeType currentStorgeType = PTXStorgeType.YAML; // TODO 从配置文件中读取

    @Override
    public void onEnable() {
        register();
        log.info(String.format("插件已启用，版本: %s", getDescription().getVersion()) +
                "\n数据库: " + currentStorgeType +
                "\n前置：" +
                        "\n - Vault: " + (isEconomyEnabled ? "已启用" : "未安装") +
                        "\n - PlayerPoints: " + (isPlayerPointsEnabled ? "已启用" : "未安装")
        );

    }

    @Override
    public void onDisable() {
        unregister();
        log.info("插件已禁用");
    }

    private void register() {
        // 1、创建必要的前置
        YLib ylib = new YLib(this);
        log = ylib.getLoggerTools();
        scheduler = ylib.getScheduler();

        new Metrics(this, 27726);

        if (!setupEconomy()) {
            isEconomyEnabled = false;
        }

        if (!setupPlayerPoints()) {
            isPlayerPointsEnabled = false;
        }

        // 2、创建存储工厂
        StorageFactory storageFactory = new StorageFactory(this, log, currentStorgeType);
        try {
            storageFactory.getStorageCreator().connect();
        } catch (SQLException | ClassNotFoundException e) {
            log.error("连接数据库时发生错误：" + e.getMessage());
        }

        // 3、从 tasks 目录加载所有任务添加到缓存
        List<TaskDefinition> taskDefList = storageFactory.getRepository().loadAll();
        if (taskDefList == null || taskDefList.isEmpty()) {
            log.warn("没有从存储库加载到任何任务定义");
            return;
        }
        TaskRepository taskRepository = storageFactory.getRepository();
        TaskProgressRepository taskProgressRepository = storageFactory.getProgressRepository();
        TaskCache cache = new TaskCache(log, scheduler, taskRepository, taskProgressRepository);
        ConcurrentMap<String, TaskDefinition> taskDefMap = new ConcurrentHashMap<>();
        for (TaskDefinition taskDef : taskDefList) {
            taskDefMap.putIfAbsent(taskDef.getId(), taskDef); // 避免重复添加, 如果两个任务有相同ID, 则保留第一个
        }
        cache.setTaskDefById().add(taskDefMap);
        log.debug("已将 " + taskDefList.size() + " 个任务添加到缓存");

        // 4、开始数据同步任务
        //new DataSyncTask(log, scheduler, taskCache, databaseDAO).startSync();

        // 6、注册事件
        // 初始化事件总线
        SimpleEventBus eventBus = new SimpleEventBus(log);

        // 注册事件处理程序
        registerEventHandlers(eventBus);

        // 注册 Bukkit 事件监听器
        KillListener killListener = new KillListener(eventBus);
        getServer().getPluginManager().registerEvents(killListener, this);

        // 注册玩家加入事件
        getServer().getPluginManager().registerEvents(new PlayerJoinHandler(log, cache, taskProgressRepository), this);


        // 7、注册命令
        TaskService taskService = new TaskService(log, taskRepository, taskProgressRepository);
        new CommandRegister(ylib.getCommandManager(), cache, viewFrame, taskService).registerCommands();

        // 8、注册UI界面
        try {
            viewFrame = ViewFrame.create(this);
            viewFrame.with(new MainUI()).register();
        } catch (Exception e) {
            log.error("创建UI错误" + e);
        }

        // 9、更新检查
        UpdateHelper updateHelper = new UpdateHelper(log);
        updateHelper.checkUpdate(getDescription().getVersion());

    }

    private void registerEventHandlers(EventBus eventBus) {
        // 注册 TaskProgressEvent 的处理器
        eventBus.register(TaskProgressEvent.class, event -> {
            log.info(String.format("玩家 %s 完成了动作: %s, 目标: %s, 进度: %d",
                    event.getPlayerId(),
                    event.getActionType(),
                    event.getMobType(), // 假设你给 TaskProgressEvent 加了 getMobType 方法
                    event.getProgress())); // 假设你给 TaskProgressEvent 加了 getProgress 方法

            // 在这里可以触发任务更新、发送奖励等逻辑
            // Player player = Bukkit.getPlayer(event.getPlayerId());
            // if (player != null) {
            //     player.sendMessage("你完成了一个击杀任务！");
            // }
        });
    }

    private void unregister() {
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return true;
    }

    private boolean setupPlayerPoints() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")) {
            ppAPI = PlayerPoints.getInstance().getAPI();
            return true;
        }
        return false;
    }
}
