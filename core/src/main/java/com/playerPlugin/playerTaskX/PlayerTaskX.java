package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.YLib;
import cn.yvmou.ylib.api.config.ConfigurationManager;
import cn.yvmou.ylib.api.logger.Logger;
import cn.yvmou.ylib.api.scheduler.UniversalScheduler;
import com.playerPlugin.playerTaskX.api.Enum.PTXStorgeType;
import com.playerPlugin.playerTaskX.api.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.api.storage.TaskRepository;
import com.playerPlugin.playerTaskX.api.utils.Metrics;
import com.playerPlugin.playerTaskX.api.utils.StorageUtil;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.commands.AdminCommand;
import com.playerPlugin.playerTaskX.configuration.EditorConfiguration;
import com.playerPlugin.playerTaskX.configuration.StorgeConfiguration;
import com.playerPlugin.playerTaskX.event.PlayerJoinHandler;
import com.playerPlugin.playerTaskX.storage.StorageFactory;
import com.playerPlugin.playerTaskX.web.EditorServer;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class PlayerTaskX extends JavaPlugin {
    private Logger log;
    private UniversalScheduler scheduler;
    private Economy economy;
    private ConfigurationManager configurationManager;

    private boolean isEconomyEnabled = true;
    private PlayerPointsAPI ppAPI;

    private boolean isPlayerPointsEnabled = true;

    private final PTXStorgeType currentStorgeType = PTXStorgeType.YAML; // TODO 从配置文件中读取
    
    private TaskAPI api;

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
        log.info("插件已禁用");
    }

    private void register() {
        // 1、创建必要的前置
        YLib ylib = new YLib(this);
        log = ylib.getLogger();
        scheduler = ylib.getScheduler();

        new Metrics(this, 27726);

        if (!setupEconomy()) {
            isEconomyEnabled = false;
        }

        if (!setupPlayerPoints()) {
            isPlayerPointsEnabled = false;
        }

        // 1.5 初始化一些工具类
        new StorageUtil(log);

        // 2、创建存储工厂
        StorageFactory storageFactory = new StorageFactory(this, log, currentStorgeType);
        try {
            storageFactory.getStorageCreator().connect();
        } catch (SQLException | ClassNotFoundException e) {
            log.error("连接数据库时发生错误：" + e.getMessage());
        }

        // 3、从 tasks 目录加载所有任务添加到缓存
        TaskRepository taskRepository = storageFactory.getRepository();
        TaskProgressRepository taskProgressRepository = storageFactory.getProgressRepository();
        TaskCache cache = new TaskCache(log, scheduler, storageFactory);

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

        // 注册配置
        configurationManager = ylib.getConfigurationManager();

        configurationManager.registerConfiguration(EditorConfiguration.class);
        configurationManager.registerConfiguration(StorgeConfiguration.class);

        // 7、注册命令
        api = new TaskAPI(log, taskRepository, taskProgressRepository, cache);
        ylib.getCommandManager().register(new AdminCommand(log, api, cache, configurationManager, ylib.getCommandManager()));


        // 启动服务器
        try {
            new EditorServer(this, taskRepository, api, log).start();
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
