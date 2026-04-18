# GeneralConfiguration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `GeneralConfiguration` class using YLib annotation-driven configuration and register it in `PlayerTaskX.java`.

**Scope:** This plan covers ONLY the configuration class migration. The config values (auto-save-interval, auto-backup, locales) are not currently used by any code — usage will be added separately when those features are implemented.

**Architecture:** Create a new POJO configuration class annotated with YLib's `@AutoConfiguration` and `@ConfigValue`, register it via `ConfigurationManager` in the plugin's `onEnable()`.

**Tech Stack:** YLib Configuration System, Java, Bukkit Plugin

---

## File Structure

- **Create:** `core/src/main/java/com/playerPlugin/playerTaskX/configuration/GeneralConfiguration.java`
- **Modify:** `core/src/main/java/com/playerPlugin/playerTaskX/PlayerTaskX.java`
- **Reference:** `core/src/main/java/com/playerPlugin/playerTaskX/configuration/EditorConfiguration.java` (for pattern)

---

## Tasks

### Task 1: Create GeneralConfiguration class

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/configuration/GeneralConfiguration.java`

- [ ] **Step 1: Write GeneralConfiguration class**

```java
package com.playerPlugin.playerTaskX.configuration;

import cn.yvmou.ylib.api.config.AutoConfiguration;
import cn.yvmou.ylib.api.config.ConfigValue;

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
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/configuration/GeneralConfiguration.java
git commit -m "feat: add GeneralConfiguration for config.yml management"
```

---

### Task 2: Integrate GeneralConfiguration into PlayerTaskX

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/PlayerTaskX.java`

**Scope of this task:** Register GeneralConfiguration via YLib's ConfigurationManager. No existing `getConfig()` calls are replaced — those belong to separate migrations (storage.*, editor.*).

- [ ] **Step 1: Add field, register, and remove saveDefaultConfig**

In `PlayerTaskX.java`, add field:
```java
private GeneralConfiguration generalConfig;
```

In `onEnable()`, after YLib initialization, add registration:
```java
generalConfig = ylib.getConfigurationManager().registerConfiguration(GeneralConfiguration.class);
```

Delete line `saveDefaultConfig();` (line 37) since YLib auto-generates config files.

- [ ] **Step 2: Verify the change compiles**

Run: `./mvnw compile -pl core` (or equivalent build command)
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/PlayerTaskX.java
git commit -m "feat: integrate GeneralConfiguration via YLib ConfigurationManager"
```

---

## Verification

1. Build plugin with `./mvnw clean package`
2. Start server — config.yml should be auto-generated in plugin data folder
3. Verify config.yml contains all fields with correct defaults
