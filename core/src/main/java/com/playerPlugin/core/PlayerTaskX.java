package com.playerPlugin.core;

import cn.yvmou.ylib.YLib;
import com.playerPlugin.core.EventHandlers.EventsRegister;
import com.playerPlugin.core.domain.TaskProgressManger;
import com.playerPlugin.core.UI.MainUI;
import com.playerPlugin.core.configs.ConfigManager;
import com.playerPlugin.core.configs.TaskConfig;
import com.playerPlugin.core.dataManager.impl.SQLiteManager;
import com.playerPlugin.core.dataManager.StorgeManager;
import com.playerPlugin.core.dataManager.StorgeTypes;
import com.playerPlugin.core.utils.Metrics;
import com.playerPlugin.core.utils.UpdateHelper;
import com.playerPlugin.core.domain.TaskManager;
import me.devnatan.inventoryframework.ViewFrame;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import static com.playerPlugin.core.utils.Help.log;

public final class PlayerTaskX extends JavaPlugin {
    private static YLib ylib;
    private static Economy economy = null;
    private static PlayerPointsAPI ppAPI = null;
    private static ViewFrame viewFrame = null;
    private ConfigManager configManager = null;
    private TaskManager taskManager = null;
    private StorgeManager storgeManager = null;

    @Nullable
    public static Economy getEconomy() {
        if (economy == null) {
            return null;
        }
        return economy;
    }

    @Nullable
    public static PlayerPointsAPI getPlayerPointsAPI() {
        if (ppAPI == null) {
            return null;
        }
        return ppAPI;
    }

    @Nullable
    public static ViewFrame getViewFrame() {
        if (viewFrame == null) {
            return null;
        }
        return viewFrame;
    }

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

        // 2、注册配置文件
        configManager = ConfigManager.getInstance();
        configManager.initTaskConfig(new TaskConfig(this));
        TaskConfig taskConfig = configManager.getTaskConfig();

        this.saveDefaultConfig();
        taskConfig.saveDefaultTaskConfig();

        // 3、注册 TaskManager
        taskManager = TaskManager.init();
        taskManager.loadTasksToCache(taskConfig);

        // 4、注册 StorgeManager 连接数据库 开始数据同步任务 TODO 数据类型暂时硬编码为 SQLITE
        storgeManager = new StorgeManager(this, StorgeTypes.SQLITE, new SQLiteManager(), taskManager);
        storgeManager.connect();
        storgeManager.getDataSyncTask().startSync();

        // 5、注册 TaskProgressManger
        taskManager.initTaskProgressManger(new TaskProgressManger(storgeManager, taskManager));

        // 6、注册事件
        EventsRegister.register(this);

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
