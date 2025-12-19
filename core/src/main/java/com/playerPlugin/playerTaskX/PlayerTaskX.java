package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.api.YLib;
import cn.yvmou.ylib.api.YLibCreate;
import cn.yvmou.ylib.api.scheduler.UniversalScheduler;
import cn.yvmou.ylib.api.services.LoggerService;
import com.playerPlugin.playerTaskX.api.Enum.PTXStorgeType;
import com.playerPlugin.playerTaskX.api.PlayerTaskXAPI;
import com.playerPlugin.playerTaskX.api.PlayerTaskXProvider;
import com.playerPlugin.playerTaskX.api.impl.PlayerTaskXAPIImpl;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.commands.CommandRegister;
import com.playerPlugin.playerTaskX.event.PlayerJoinHandler;
import com.playerPlugin.playerTaskX.model.Task.TaskDefinition;
import com.playerPlugin.playerTaskX.service.TaskService;
import com.playerPlugin.playerTaskX.storage.StorageFactory;
import com.playerPlugin.playerTaskX.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.storage.TaskRepository;
import com.playerPlugin.playerTaskX.utils.Metrics;
import com.playerPlugin.playerTaskX.utils.UpdateHelper;
import com.playerPlugin.playerTaskX.web.EditorServer;
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
    private LoggerService log;
    private UniversalScheduler scheduler;
    private Economy economy;

    private boolean isEconomyEnabled = true;
    private PlayerPointsAPI ppAPI;

    private boolean isPlayerPointsEnabled = true;

    private final PTXStorgeType currentStorgeType = PTXStorgeType.YAML; // TODO 从配置文件中读取
    
    private PlayerTaskXAPI api;

    @Override
    public void onEnable() {
        register();
        log.info("插件已启用" +
                "\n==============================================" +
                "\n插件版本: " + getDescription().getVersion() +
                "\n数据库: " + currentStorgeType +
                "\n前置：" +
                        "\n - Vault: " + (isEconomyEnabled ? "已启用" : "未安装") +
                        "\n - PlayerPoints: " + (isPlayerPointsEnabled ? "已启用" : "未安装") +
                "\n=============================================="
        );

    }

    @Override
    public void onDisable() {
        unregister();
        log.info("插件已禁用");
    }

    private void register() {
        // 1、创建必要的前置
        YLib ylib = YLibCreate.create(this);
        log = ylib.getSimpleLogger();
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
        //SimpleEventBus eventBus = new SimpleEventBus(log);

        // 注册事件处理程序
        //registerEventHandlers(eventBus);

        // 注册 Bukkit 事件监听器
        //KillListener killListener = new KillListener(eventBus);
        //getServer().getPluginManager().registerEvents(killListener, this);

        // 注册玩家加入事件
        getServer().getPluginManager().registerEvents(new PlayerJoinHandler(log, cache, taskProgressRepository), this);

        // 7、注册命令
        TaskService taskService = new TaskService(log, taskRepository, taskProgressRepository, cache);
        new CommandRegister(ylib.getSimpleCommandManager(), cache, taskService).registerCommands();

        // 8、初始化并注册 API
        api = new PlayerTaskXAPIImpl(log, cache, taskRepository, taskProgressRepository);
        PlayerTaskXProvider.setApi(api);
        log.info("PlayerTaskX API " + api.getApiVersion() + " 已注册，其他插件现在可以使用 API");



        // 9、更新检查
        UpdateHelper updateHelper = new UpdateHelper(log);
        updateHelper.checkUpdate(getDescription().getVersion());

        // 启动服务器
        try {
            new EditorServer(this, taskRepository, taskService, log).start();
        } catch (Exception e) {
            log.error("启动服务器时发生错误：" + e.getMessage());
        }

    }

//    private void registerEventHandlers(EventBus eventBus) {
//        // 注册 TaskProgressEvent 的处理器
//        eventBus.register(TaskProgressEvent.class, event -> {
//            log.info(String.format("玩家 %s 完成了动作: %s, 目标: %s, 进度: %d",
//                    event.getPlayerId(),
//                    event.getActionType(),
//                    event.getMobType(), // 假设你给 TaskProgressEvent 加了 getMobType 方法
//                    event.getProgress())); // 假设你给 TaskProgressEvent 加了 getProgress 方法
//
//            // 在这里可以触发任务更新、发送奖励等逻辑
//            // Player player = Bukkit.getPlayer(event.getPlayerId());
//            // if (player != null) {
//            //     player.sendMessage("你完成了一个击杀任务！");
//            // }
//        });
//    }

    private void unregister() {
        // 重置 API
        PlayerTaskXProvider.reset();
        log.debug("PlayerTaskX API 已重置");
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
