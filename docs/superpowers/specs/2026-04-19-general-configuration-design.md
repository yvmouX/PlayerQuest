# Design: Migrate all Bukkit native config to YLib Configuration System

## Context

PlayerTaskX uses a hybrid configuration approach:
- `config.yml` (auto-save, backup, locale) still uses Bukkit native `getConfig()`
- `StorgeConfiguration` and `EditorConfiguration` use YLib annotations but keys don't match YAML files
- `PlayerTaskX.java` directly reads storage and editor settings via `getConfig()`

This design covers migrating ALL remaining Bukkit native config to YLib.

## Solution

Rewrite `StorgeConfiguration` and `EditorConfiguration` to match actual YAML structure, then update `PlayerTaskX.java` to use all three YLib configs.

### 1. GeneralConfiguration (new)

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

### 2. StorgeConfiguration (rewrite)

Matches `storge.yml` structure, provides what `PlayerTaskX.java` actually needs.

```java
@AutoConfiguration(configFile = "storge.yml", version = "1.0.0")
public class StorgeConfiguration {
    @ConfigValue(value = "storage-type", description = "存储类型: YAML, SQLITE, MYSQL")
    private String storageType = "SQLITE";

    @ConfigValue(value = "mysql.host", description = "MySQL主机")
    private String mysqlHost = "localhost";

    @ConfigValue(value = "mysql.port", description = "MySQL端口")
    private int mysqlPort = 3306;

    @ConfigValue(value = "mysql.database", description = "MySQL数据库名")
    private String mysqlDatabase = "quest";

    @ConfigValue(value = "mysql.username", description = "MySQL用户名")
    private String mysqlUsername = "root";

    @ConfigValue(value = "mysql.password", description = "MySQL密码")
    private String mysqlPassword = "";
}
```

### 3. EditorConfiguration (rewrite)

Matches `editor.yml` structure (editor.port, editor.host).

```java
@AutoConfiguration(configFile = "editor.yml", version = "1.0.0")
public class EditorConfiguration {
    @ConfigValue(value = "editor.port", description = "编辑器端口")
    private int port = 8080;

    @ConfigValue(value = "editor.host", description = "编辑器绑定地址")
    private String host = "127.0.0.1";
}
```

### 4. PlayerTaskX.java Changes

- Register all three configurations via `ConfigurationManager.registerConfiguration()`
- Replace all `getConfig()` calls with YLib config getters
- Remove `saveDefaultConfig()` call

### Notes

- YAML files remain unchanged (YLib auto-generates from annotations)
- Existing YAML files in resources serve as default templates
