package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.YLib;
import com.playerPlugin.playerTaskX.EventHandlers.EventsRegister;
import com.playerPlugin.playerTaskX.UI.MainUI;
import com.playerPlugin.playerTaskX.commands.AcceptCmd;
import com.playerPlugin.playerTaskX.commands.MeCmd;
import com.playerPlugin.playerTaskX.commands.OpenCmd;
import com.playerPlugin.playerTaskX.commands.admin.ListCmd;
import com.playerPlugin.playerTaskX.commands.admin.ReloadCmd;
import com.playerPlugin.playerTaskX.commands.admin.StartCmd;
import com.playerPlugin.playerTaskX.configs.ConfigManager;
import com.playerPlugin.playerTaskX.configs.TaskConfig;
import com.playerPlugin.playerTaskX.dataManager.impl.SQLiteManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeTypes;
import com.playerPlugin.playerTaskX.utils.Logger;
import com.playerPlugin.playerTaskX.utils.Metrics;
import com.playerPlugin.playerTaskX.utils.UpdateHelper;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import me.devnatan.inventoryframework.ViewFrame;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class PlayerTaskX extends JavaPlugin {
    private static YLib ylib;
    private static Economy economy = null;
    private static PlayerTaskX instance;
    public static Logger log;
    private static ViewFrame viewFrame = null;

    public static YLib getYLib() {
        return ylib;
    }

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
        // 首先初始化日志系统
        log = new Logger();
        instance = this;
        ylib = new YLib(this);

        register();
        log.info(Logger.prefix + "插件已启用");
    }

    @Override
    public void onDisable() {
        if (log != null) {
            log.info(Logger.prefix + "正在关闭插件...");
            try {
                StorgeManager.getInstance().close();
                log.info(Logger.prefix + "数据库连接已关闭");
            } catch (Exception e) {
                log.err(Logger.prefix + "关闭数据库连接时发生错误：" + e.getMessage());
            }
            log.info(Logger.prefix + "插件已禁用");
        }
        unregister();
    }

    private void register() {
        new Metrics(this, 27726);

        // 初始化Vault
        if (!setupEconomy()) {
            log.err(Logger.prefix + "Vault未安装");
        }

        // 任务配置
        TaskConfig taskConfig = new TaskConfig(this);
        // 配置文件
        ConfigManager configManager = new ConfigManager(this, taskConfig);
        configManager.saveAllDefaultConfigs();

        // 数据库初始化（在TaskManager之前）
        // TODO 数据类型暂时硬编码为 SQLITE
        StorgeManager.init(this, StorgeTypes.SQLITE, new SQLiteManager());
        StorgeManager.getInstance().connect();
        StorgeManager.getInstance().createTable();

        // 必须在 configManager.saveAllDefaultConfigs() 和 数据库初始化 后调用
        TaskManager.init(taskConfig);
        
        // 加载所有玩家任务数据（必须在TaskManager初始化后）
        TaskManager.getInstance().loadAllPlayerTasks();

        // 事件
        EventsRegister.register();
        
        // 命令
        getYLib().getCommandManager().registerCommands("playertaskx",
                new MeCmd(),
                new AcceptCmd(),
                new OpenCmd()
        );
        getYLib().getCommandManager().registerCommands("ptx",
                new MeCmd(),
                new AcceptCmd(),
                new OpenCmd()
        );
        getYLib().getCommandManager().registerCommands("playertaskxadmin",
                new ReloadCmd(this, taskConfig),
                new ListCmd(),
                new StartCmd()
        );
        getYLib().getCommandManager().registerCommands("ptxa",
                new ReloadCmd(this, taskConfig),
                new ListCmd(),
                new StartCmd()
        );

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
            log.err("创建UI错误" + e);
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
