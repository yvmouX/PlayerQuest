package com.playerPlugin.playerTaskX.configs;

import com.playerPlugin.playerTaskX.PlayerTaskX;

public class ConfigManager {
    private final PlayerTaskX plugin;
    private final TaskConfig taskConfig;

    public ConfigManager(PlayerTaskX plugin, TaskConfig taskConfig) {
        this.plugin = plugin;
        this.taskConfig = taskConfig;
    }


    /**
     * 创建所有配置文件
     * <p>
     *     不会覆盖原有的配置，仅用作生成配置文件
     * </p>
     */
    public void saveAllDefaultConfigs() {
        plugin.saveDefaultConfig();
        taskConfig.saveDefaultTaskConfig();
    }

    public void reloadAllDefaultConfigs() {
        plugin.reloadConfig();
        taskConfig.reloadTasksConfig();
    }
}
