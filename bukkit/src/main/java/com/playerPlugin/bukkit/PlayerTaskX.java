package com.playerPlugin.bukkit;

import cn.yvmou.ylib.YLib;
import cn.yvmou.ylib.api.scheduler.UniversalScheduler;
import cn.yvmou.ylib.tools.LoggerTools;
import com.paperPlugin.api.events.TaskProgressEvent;
import com.playerPlugin.bukkit.listeners.KillListener;
import com.playerPlugin.core.event.SimpleEventBus;
import com.playerPlugin.bukkit.UI.MainUI;
import com.playerPlugin.bukkit.commands.CommandRegister;
import com.playerPlugin.bukkit.configs.ConfigManager;
import com.playerPlugin.bukkit.configs.TaskConfig;
import com.playerPlugin.core.repository.RepositoryCreator;
import com.playerPlugin.infra.Infra;
import com.playerPlugin.common.Enum.StorgeTypes;
import com.playerPlugin.core.utils.UpdateHelper;
import com.playerPlugin.infra.cache.CacheDAO;
import com.playerPlugin.infra.cache.DatabaseDAO;
import com.playerPlugin.infra.cache.TaskCache;
import com.playerPlugin.infra.storage.DataSyncTask;
import com.playerPlugin.infra.storage.sqlite.SQLiteRepositoryCreator;
import me.devnatan.inventoryframework.ViewFrame;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class PlayerTaskX extends JavaPlugin {
    private YLib ylib;
    private LoggerTools log;
    private UniversalScheduler scheduler;
    private Economy economy;
    private PlayerPointsAPI ppAPI;
    private ViewFrame viewFrame;
    private ConfigManager configManager;
    public StorgeTypes storgeTypes;

    @Override
    public void onEnable() {
        register();
        log.info("插件已启用");
    }

    @Override
    public void onDisable() {
        unregister();
        log.info("插件已禁用");
    }

    private void register() {
        // 1、注册必要的前置
        ylib = new YLib(this);
        log = ylib.getLoggerTools();
        scheduler = ylib.getScheduler();

        new Metrics(this, 27726);

        if (!setupEconomy()) {
            log.error("Vault未安装");
        }

        if (!setupPlayerPoints()) {
            log.error("PlayerPoints未安装");
        }

        // 2、注册/保存配置文件
        configManager = ConfigManager.getInstance();
        configManager.initTaskConfig(new TaskConfig(this));
        TaskConfig taskConfig = configManager.getTaskConfig();

        this.saveDefaultConfig();
        taskConfig.saveDefaultTaskConfig();

        // 加载任务到缓存
        ReadConfigToCache readConfigToCache = new ReadConfigToCache(log);
        readConfigToCache.loadTasksToCache(taskConfig);

        // 创建 SQLITE 表
        storgeTypes = StorgeTypes.SQLITE;
        if (storgeTypes == StorgeTypes.SQLITE) {
            SQLiteRepositoryCreator creator = new SQLiteRepositoryCreator();
            try {
                creator.connect(this); // 连接并创建表
            } catch (SQLException | ClassNotFoundException e) {
                log.error("创建SQLITE表时发生错误：" + e.getMessage());
            }
        }
        // 开始数据同步任务
        RepositoryCreator repositoryCreator = new Infra().getStorageCreator();
        TaskCache taskCache = new TaskCache();
        CacheDAO cacheDAO = new CacheDAO(log);
        DatabaseDAO databaseDAO = new DatabaseDAO(log, repositoryCreator, cacheDAO);
        new DataSyncTask(log, scheduler, taskCache, databaseDAO).startSync();

        // 6、注册事件
        // 初始化事件总线
        SimpleEventBus eventBus = new SimpleEventBus();

        // 注册事件处理程序
        registerEventHandlers();

        // 注册 Bukkit 事件监听器
        KillListener killListener = new KillListener(eventBus);
        getServer().getPluginManager().registerEvents(killListener, this);


        // 7、注册命令
        new CommandRegister(this, ylib, taskConfig, storgeManager, taskManager).registerCommands();

        // 8、注册UI界面
        try {
            viewFrame = ViewFrame.create(this);
            viewFrame.with(new MainUI()).register();
        } catch (Exception e) {
            log.error("创建UI错误" + e);
        }

        // 9、更新检查
        UpdateHelper updateHelper = new UpdateHelper();
        updateHelper.checkUpdate(getDescription().getVersion());

    }

    private void registerEventHandlers() {
        // 注册 TaskProgressEvent 的处理器
        eventBus.register(TaskProgressEvent.class, event -> {
            getLogger().info(String.format("玩家 %s 完成了动作: %s, 目标: %s, 进度: %d",
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
        try {
            taskManager.shutdown();
            log.info("任务数据已保存");
        } catch (Exception e) {
            log.error("关闭任务数据时发生错误：" + e.getMessage());
        }
        try {
            storgeManager.close();
        } catch (Exception e) {
            log.error("关闭数据库连接时发生错误：" + e.getMessage());
        }
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
