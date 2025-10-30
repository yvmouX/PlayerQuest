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
import com.playerPlugin.playerTaskX.dataManager.SQLiteManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeTypes;
import com.playerPlugin.playerTaskX.utils.Logger;
import com.playerPlugin.playerTaskX.utils.Metrics;
import com.playerPlugin.playerTaskX.utils.UpdateHelper;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import me.devnatan.inventoryframework.ViewFrame;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class PlayerTaskX extends JavaPlugin {
    private static YLib ylib;
    private static PlayerTaskX instance;
    public static Logger log;
    private static ViewFrame viewFrame = null;

    public static YLib getYLib() {
        return ylib;
    }

    public static PlayerTaskX getInstance() {
        return instance;
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
            log.info(Logger.prefix + "插件已禁用");
        }
        unregister();
    }

    private void register() {
        new Metrics(this, 27726);

        // 更新
        UpdateHelper updateHelper = new UpdateHelper();
        updateHelper.checkUpdate(getDescription().getVersion());

        //任务配置
        TaskConfig taskConfig = new TaskConfig(this);
        TaskManager.init(taskConfig);

        // 配置文件
        ConfigManager configManager = new ConfigManager(this, taskConfig);
        configManager.saveAllDefaultConfigs();

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

        // 数据库
        // TODO 数据类型暂时硬编码为 SQLITE
        StorgeManager storgeManager = new StorgeManager(this, StorgeTypes.SQLITE, new SQLiteManager());
        storgeManager.connect();
        storgeManager.createTable();
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
}
