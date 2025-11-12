package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.YLib;
import com.playerPlugin.playerTaskX.EventHandlers.EventsRegister;
import com.playerPlugin.playerTaskX.UI.MainUI;
import com.playerPlugin.playerTaskX.commands.CommandRegister;
import com.playerPlugin.playerTaskX.configs.ConfigManager;
import com.playerPlugin.playerTaskX.configs.TaskConfig;
import com.playerPlugin.playerTaskX.dataManager.impl.SQLiteManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeTypes;
import com.playerPlugin.playerTaskX.utils.Metrics;
import com.playerPlugin.playerTaskX.utils.Help;
import com.playerPlugin.playerTaskX.utils.UpdateHelper;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import me.devnatan.inventoryframework.ViewFrame;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import static com.playerPlugin.playerTaskX.utils.Help.log;
import static com.playerPlugin.playerTaskX.utils.Help.tm;

public final class PlayerTaskX extends JavaPlugin {
    private static YLib ylib;
    private static Economy economy = null;
    private static PlayerTaskX instance;
    private static ViewFrame viewFrame = null;

    public static PlayerTaskX getInstance() {
        return instance;
    }

    public static Economy getEconomy() {
        return economy;
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
        instance = this;
        ylib = new YLib(this);

        register();
        log.info("插件已启用");
    }

    @Override
    public void onDisable() {
        if (log != null) {
            log.info("正在关闭插件...");
            // 关闭任务管理器，保存所有数据
            try {
                tm.shutdown();
                log.info("任务数据已保存");
            } catch (IllegalStateException ignored) { }
            try {
                final StorgeManager sm = StorgeManager.getInstance();
                if (sm != null) {
                    sm.close();
                    log.info("数据库连接已关闭");
                }
            } catch (Exception e) {
                log.error("关闭数据库连接时发生错误：" + e.getMessage());
            }
            log.info("插件已禁用");
        }
        unregister();
    }

    private void register() {
        new Metrics(this, 27726);

        // 初始化Vault
        if (!setupEconomy()) {
            log.error("Vault未安装");
        }

        // 任务配置
        TaskConfig taskConfig = new TaskConfig(this);
        // 配置文件
        ConfigManager configManager = new ConfigManager(this, taskConfig);
        configManager.saveAllDefaultConfigs();

        // 先初始化任务管理器（不依赖数据库）
        TaskManager.init(taskConfig);

        // 再初始化数据库管理器
        // TODO 数据类型暂时硬编码为 SQLITE
        StorgeManager.init(this, StorgeTypes.SQLITE, new SQLiteManager());
        StorgeManager.getInstance().connect();

        Help.tm = TaskManager.getInstance();
        Help.sm = StorgeManager.getInstance();

        // 启动缓存定时任务（依赖于 TaskManager 和 StorgeManager 已完成初始化）
        StorgeManager.getInstance().getDataSyncTask().stopSync();

        // 事件
        EventsRegister.register();

        // 命令
        new CommandRegister(this, ylib, taskConfig).registerCommands();

        // UI界面
        registerViews();

        // 更新
        UpdateHelper updateHelper = new UpdateHelper();
        updateHelper.checkUpdate(getDescription().getVersion());

    }

    private void unregister() {
        log = null;
        instance = null;
    }

    private void registerViews() {
        try {
            viewFrame = ViewFrame.create(this);
            viewFrame.with(new MainUI()).register();
        } catch (Exception e) {
            log.error("创建UI错误" + e);
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
}
