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
import com.playerPlugin.playerTaskX.commands.UserCommand;
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

public final class PlayerTaskX extends JavaPlugin {
    private Logger log;
    private UniversalScheduler scheduler;
    private ConfigurationManager configurationManager;
    private PTXStorgeType currentStorgeType = PTXStorgeType.YAML;
    private TaskAPI api;

    // Hook
    private Economy economy;
    private boolean isEconomyEnabled = true;
    private PlayerPointsAPI ppAPI;
    private boolean isPlayerPointsEnabled = true;

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

        // 注册配置
        configurationManager = ylib.getConfigurationManager();
        configurationManager.registerConfiguration(EditorConfiguration.class);
        StorgeConfiguration storgeConfig = configurationManager.registerConfiguration(StorgeConfiguration.class);

        // 读取存储配置
        try {
            currentStorgeType = PTXStorgeType.valueOf(storgeConfig.getStorageMethod_playerData().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("无效的存储方式: " + storgeConfig.getStorageMethod_playerData() + ", 将使用 YAML");
            currentStorgeType = PTXStorgeType.YAML;
        }

        // 2、创建存储工厂
        StorageFactory storageFactory = new StorageFactory(this, log, currentStorgeType);

        // 3、从 tasks 目录加载所有任务添加到缓存
        TaskRepository taskRepository = storageFactory.getRepository();
        TaskProgressRepository taskProgressRepository = storageFactory.getProgressRepository();
        TaskCache cache = new TaskCache(log, scheduler, storgeConfig, storageFactory);

        // 注册玩家加入事件
        getServer().getPluginManager().registerEvents(new PlayerJoinHandler(log, cache, taskProgressRepository), this);

        // 4、注册任务事件监听
        api = new TaskAPI(log, storageFactory, cache);
        com.playerPlugin.playerTaskX.event.TaskRouter router = new com.playerPlugin.playerTaskX.event.TaskRouter(api, log);
        getServer().getPluginManager().registerEvents(new com.playerPlugin.playerTaskX.event.listeners.KillListener(router), this);
        getServer().getPluginManager().registerEvents(new com.playerPlugin.playerTaskX.event.listeners.BlockListener(router), this);
        getServer().getPluginManager().registerEvents(new com.playerPlugin.playerTaskX.event.listeners.InventoryListener(router), this);
        getServer().getPluginManager().registerEvents(new com.playerPlugin.playerTaskX.event.listeners.EntityListener(router), this);
        getServer().getPluginManager().registerEvents(new com.playerPlugin.playerTaskX.event.listeners.InteractListener(router), this);

        // 7、注册命令
        ylib.getCommandManager().register(new AdminCommand(log, api, cache, configurationManager, ylib.getCommandManager()));
        ylib.getCommandManager().register(new UserCommand(log, api));


        // 启动服务器
        try {
            new EditorServer(this, taskRepository, api, log).start();
        } catch (Exception e) {
            log.error("启动服务器时发生错误：" + e.getMessage());
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
