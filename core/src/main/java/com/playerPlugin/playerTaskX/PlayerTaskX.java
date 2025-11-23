package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.YLib;
import cn.yvmou.ylib.api.scheduler.UniversalScheduler;
import cn.yvmou.ylib.tools.LoggerTools;
import com.playerPlugin.playerTaskX.UI.MainUI;
import com.playerPlugin.playerTaskX.cache.CacheDAO;
import com.playerPlugin.playerTaskX.cache.DatabaseDAO;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.commands.CommandRegister;
import com.playerPlugin.playerTaskX.common.Enum.PTXStorgeType;
import com.playerPlugin.playerTaskX.configs.ConfigManager;
import com.playerPlugin.playerTaskX.domain.Task.TaskDefinition;
import com.playerPlugin.playerTaskX.event.PlayerJoinHandler;
import com.playerPlugin.playerTaskX.event.SimpleEventBus;
import com.playerPlugin.playerTaskX.storage.DataSyncTask;
import com.playerPlugin.playerTaskX.storage.RepositoryCreator;
import com.playerPlugin.playerTaskX.storage.StorageFactory;
import com.playerPlugin.playerTaskX.storage.TaskRepository;
import com.playerPlugin.playerTaskX.storage.sqlite.SQLiteRepositoryCreator;
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

public final class PlayerTaskX extends JavaPlugin {
    private LoggerTools log;
    private UniversalScheduler scheduler;
    private Economy economy;
    private boolean isEconomyEnabled = true;
    private PlayerPointsAPI ppAPI;
    private boolean isPlayerPointsEnabled = true;
    private ViewFrame viewFrame;
    private ConfigManager configManager;

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

        // 创建存储工厂
        StorageFactory storageFactory = new StorageFactory(this, log, currentStorgeType);
        try {
            storageFactory.getStorageCreator().connect();
        } catch (SQLException | ClassNotFoundException e) {
            log.error("连接数据库时发生错误：" + e.getMessage());
        }

        // 从 tasks 目录加载所有任务添加到缓存
        List<TaskDefinition> taskDefList = storageFactory.getRepository().loadAll();
        TaskCache cache = new TaskCache();
        for (TaskDefinition taskDef : taskDefList) {
            cache.getTaskDefList().add(taskDef);
            log.debug("已将 " + taskDefList.size() + " 个任务添加到缓存");
        }

        // 开始数据同步任务
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

        // 注册玩家加入事件
        getServer().getPluginManager().registerEvents(new PlayerJoinHandler(new StorageFactory(this, log)), this);


        // 7、注册命令
        new CommandRegister(this, ylib, taskConfig).registerCommands();

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
