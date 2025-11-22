package com.playerPlugin.bukkit;

import cn.yvmou.ylib.YLib;
import com.playerPlugin.core.event.SimpleEventBus;
import com.playerPlugin.bukkit.UI.MainUI;
import com.playerPlugin.bukkit.bridge.BukkitEventBridge;
import com.playerPlugin.bukkit.commands.CommandRegister;
import com.playerPlugin.bukkit.configs.ConfigManager;
import com.playerPlugin.bukkit.configs.TaskConfig;
import com.playerPlugin.infra.dataManager.StorgeManager;
import com.playerPlugin.infra.dataManager.StorgeTypes;
import com.playerPlugin.core.utils.Metrics;
import com.playerPlugin.core.utils.UpdateHelper;
import com.playerPlugin.bukkit.listeners.EventsRegister;
import com.playerPlugin.infra.storage.sqlite.SQLiteRepositoryCreator;
import me.devnatan.inventoryframework.ViewFrame;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

import static com.playerPlugin.core.utils.Help.log;

public final class PlayerTaskX extends JavaPlugin {
    private static YLib ylib;
    private Economy economy;
    private PlayerPointsAPI ppAPI;
    private ViewFrame viewFrame;
    private ConfigManager configManager;
    private StorgeManager storgeManager;

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
        ReadConfigToCache readConfigToCache = new ReadConfigToCache();
        readConfigToCache.loadTasksToCache(taskConfig);

        // 创建 SQLITE 表
        StorgeTypes type;
        type = StorgeTypes.SQLITE;
        if (type == StorgeTypes.SQLITE) {
            SQLiteRepositoryCreator creator = new SQLiteRepositoryCreator();
            try {
                creator.connect(this); // 连接并创建表
            } catch (SQLException | ClassNotFoundException e) {
                log.error("创建SQLITE表时发生错误：" + e.getMessage());
            }
        }
        // 开始数据同步任务
        storgeManager.getDataSyncTask().startSync();



        // 5、注册 TaskProgressManger
        new TaskProgressManger(storgeManager);

        // 6、注册事件
        new EventsRegister(this, new BukkitEventBridge(new SimpleEventBus())).register();

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
