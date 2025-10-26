package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.YLib;
import com.playerPlugin.playerTaskX.EventHandlers.EventsRegister;
import com.playerPlugin.playerTaskX.commands.AcceptCmd;
import com.playerPlugin.playerTaskX.commands.MeCmd;
import com.playerPlugin.playerTaskX.commands.admin.ListCmd;
import com.playerPlugin.playerTaskX.commands.admin.ReloadCmd;
import com.playerPlugin.playerTaskX.configs.ConfigManager;
import com.playerPlugin.playerTaskX.configs.TaskConfig;
import com.playerPlugin.playerTaskX.utils.Logger;
import com.playerPlugin.playerTaskX.utils.UpdateHelper;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerTaskX extends JavaPlugin {
    private static YLib ylib;
    private static PlayerTaskX instance;

    public static Logger log;

    public static YLib getYLib() {
        return ylib;
    }

    public static PlayerTaskX getInstance() {
        return instance;
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
        // 更新
        UpdateHelper updateHelper = new UpdateHelper();
        updateHelper.checkUpdate(getDescription().getVersion());

        //任务配置
        TaskConfig taskConfig = new TaskConfig(this);
        new TaskManager(taskConfig);

        // 配置文件
        ConfigManager configManager = new ConfigManager(this, taskConfig);
        configManager.saveAllDefaultConfigs();

        // 事件
        EventsRegister.register();
        
        // 命令
        getYLib().getCommandManager().registerCommands("playertaskx",
                new MeCmd(),
                new AcceptCmd()
        );
        getYLib().getCommandManager().registerCommands("ptx",
                new MeCmd(),
                new AcceptCmd()
        );
        getYLib().getCommandManager().registerCommands("playertaskxadmin",
                new ReloadCmd(this, taskConfig),
                new ListCmd()
        );
        getYLib().getCommandManager().registerCommands("ptxa",
                new ReloadCmd(this, taskConfig),
                new ListCmd()
        );
    }

    private void unregister() {
        log = null;
        instance = null;
    }
}
