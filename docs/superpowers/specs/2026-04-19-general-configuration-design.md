# Design: Migrate config.yml to YLib Configuration System

## Context

PlayerTaskX uses a hybrid configuration approach:
- `StorgeConfiguration` and `EditorConfiguration` already use YLib annotations
- `config.yml` (auto-save, backup, locale settings) still uses Bukkit native `getConfig()`
- `PlayerTaskX.java` directly reads `storage.type` and `editor.port` from Bukkit config

This design covers migrating `config.yml` to YLib.

## Solution

Create `GeneralConfiguration` class using YLib's annotation-driven configuration system.

### New File

**Path**: `core/src/main/java/com/playerPlugin/playerTaskX/configuration/GeneralConfiguration.java`

```java
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
}
```

### PlayerTaskX.java Changes

1. Register `GeneralConfiguration` via `ConfigurationManager.registerConfiguration(GeneralConfiguration.class)`
2. Replace `getConfig().getInt("auto-save-interval", 5)` with `generalConfig.getAutoSaveInterval()`
3. Remove `saveDefaultConfig()` call (YLib handles this automatically)

## Notes

- `config.yml` filename remains unchanged (YLib generates it from `@AutoConfiguration(configFile = "config.yml", ...)`)
- Existing `config.yml` in resources will be used as default template
- Configuration validation and hot-reload are available via YLib after migration
