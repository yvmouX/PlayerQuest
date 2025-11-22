package com.playerPlugin.bukkit.configs;

public class ConfigManager {
    private TaskConfig taskConfig;

    private ConfigManager() {
    }

    public static ConfigManager getInstance() {
        return ConfigManagerHolder.INSTANCE;
    }

    private static class ConfigManagerHolder {
        private static final ConfigManager INSTANCE = new ConfigManager();
    }

    public void initTaskConfig(TaskConfig taskConfig) {
        this.taskConfig = taskConfig;
    }

    public TaskConfig getTaskConfig() {
        return taskConfig;
    }
}
