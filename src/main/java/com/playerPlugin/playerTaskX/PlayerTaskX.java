package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.YLib;
import com.playerPlugin.playerTaskX.EventHandlers.EventsRegister;
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
    }

    private void unregister() {
        log = null;
        instance = null;
    }
}
