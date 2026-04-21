package com.playerPlugin.playerTaskX.configuration;

import cn.yvmou.ylib.config.AutoConfiguration;
import cn.yvmou.ylib.config.ConfigValue;

import java.util.List;

@AutoConfiguration(configFile = "config.yml", version = "1.0.0")
public class GeneralConfiguration {

    @ConfigValue(value = "auto-save-interval", description = "自动保存间隔（分钟）")
    private int autoSaveInterval = 5;

    @ConfigValue(value = "auto-backup", description = "是否启用自动备份")
    private boolean autoBackup = true;

    @ConfigValue(value = "backup-keep-days", description = "备份保留天数")
    private int backupKeepDays = 7;

    @ConfigValue(value = "locales.default", description = "默认语言")
    private String localesDefault = "zh-CN";

    @ConfigValue(value = "locales.available", description = "可用语言列表")
    private List<String> localesAvailable = List.of("zh-CN", "en-US", "ja-JP");

    @ConfigValue(value = "locales.directory", description = "语言文件目录")
    private String localesDirectory = "locales";

    public GeneralConfiguration() {
    }

    public int getAutoSaveInterval() {
        return autoSaveInterval;
    }

    public boolean isAutoBackup() {
        return autoBackup;
    }

    public int getBackupKeepDays() {
        return backupKeepDays;
    }

    public String getLocalesDefault() {
        return localesDefault;
    }

    public List<String> getLocalesAvailable() {
        return localesAvailable;
    }

    public String getLocalesDirectory() {
        return localesDirectory;
    }
}
