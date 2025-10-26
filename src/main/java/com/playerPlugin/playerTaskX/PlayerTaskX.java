package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.YLib;
import com.playerPlugin.playerTaskX.EventHandlers.EventsRegister;
import com.playerPlugin.playerTaskX.commands.ReloadCmd;
import com.playerPlugin.playerTaskX.configs.ConfigManager;
import com.playerPlugin.playerTaskX.configs.TaskConfig;
import com.playerPlugin.playerTaskX.utils.Logger;
import com.playerPlugin.playerTaskX.utils.UpdateHelper;
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
        register();
        new ConfigManager(this, new TaskConfig(this)).saveAllDefaultConfigs();
        log.info(Logger.prefix + "插件已启用");
    }

    @Override
    public void onDisable() {
        unregister();
        log.info(Logger.prefix + "插件已禁用");
    }

    private void register() {
        log = new Logger();
        instance = this;
        ylib = new YLib(this);

        UpdateHelper updateHelper = new UpdateHelper();
        updateHelper.checkUpdate(getDescription().getVersion());

        EventsRegister.register();

        // 命令
        getYLib().getCommandManager().registerCommands("playertaskx",
                new ReloadCmd(this, new TaskConfig(this))
        );
        getYLib().getCommandManager().registerCommands("ptx",
                new ReloadCmd(this, new TaskConfig(this))
        );
        getYLib().getCommandManager().registerCommands("ptxa",
                new ReloadCmd(this, new TaskConfig(this))
        );
    }

    private void unregister() {
        log = null;
        instance = null;
    }
}
