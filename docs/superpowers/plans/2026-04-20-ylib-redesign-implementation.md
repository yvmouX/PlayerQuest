# YLib 重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 YLib 库，支持库模式和插件模式两种使用方式

**Architecture:** 将当前模块拆分为 YLib-api（接口）、YLib-core（核心实现）、YLib-platform（平台实现）、YLib-plugin（插件模式入口）四个模块。通过 YLib.init(plugin) 统一初始化，提供静态便捷方法访问核心服务。

**Tech Stack:** Gradle (Kotlin DSL), Java 8+, Bukkit/Spigot/Paper/Folia API, SPI (ServiceLoader)

---

## 文件结构映射

### 新建模块

| 模块 | 路径 |
|------|------|
| YLib-api | `YLib/YLib-api/` |
| YLib-core | `YLib/YLib-core/` |
| YLib-platform | `YLib/YLib-platform/` |
| YLib-plugin | `YLib/YLib-plugin/` |

### 接口文件（YLib-api）

| 文件 | 来源 |
|------|------|
| `YLib-api/src/main/java/cn/yvmou/ylib/api/scheduler/UniversalScheduler.java` | 从 `core/api/scheduler/` 移动 |
| `YLib-api/src/main/java/cn/yvmou/ylib/api/scheduler/UniversalTask.java` | 从 `core/api/scheduler/` 移动 |
| `YLib-api/src/main/java/cn/yvmou/ylib/api/scheduler/UniversalRunnable.java` | 从 `core/api/scheduler/` 移动 |
| `YLib-api/src/main/java/cn/yvmou/ylib/api/logger/Logger.java` | 从 `core/api/logger/` 移动 |
| `YLib-api/src/main/java/cn/yvmou/ylib/api/command/CommandManager.java` | 从 `core/api/command/` 移动 |
| `YLib-api/src/main/java/cn/yvmou/ylib/api/config/ConfigurationManager.java` | 从 `core/api/config/` 移动 |
| `YLib-api/src/main/java/cn/yvmou/ylib/api/config/ConfigValue.java` | 从 `core/api/config/` 移动 |
| `YLib-api/src/main/java/cn/yvmou/ylib/api/config/AutoConfiguration.java` | 从 `core/api/config/` 移动 |
| `YLib-api/src/main/java/cn/yvmou/ylib/api/config/ConfigurationValidationResult.java` | 从 `core/api/config/` 移动 |

### 核心实现文件（YLib-core）

| 文件 | 操作 |
|------|------|
| `YLib-core/src/main/java/cn/yvmou/ylib/YLib.java` | 新建（主入口类） |
| `YLib-core/src/main/java/cn/yvmou/ylib/internal/PlatformDetector.java` | 新建 |
| `YLib-core/src/main/java/cn/yvmou/ylib/logger/LoggerImpl.java` | 从 `core/logger/` 移动 |
| `YLib-core/src/main/java/cn/yvmou/ylib/command/*` | 从 `core/command/` 移动（除 api 包） |
| `YLib-core/src/main/java/cn/yvmou/ylib/config/*` | 从 `core/config/` 移动（除 api 包） |
| `YLib-core/src/main/java/cn/yvmou/ylib/utils/*` | 从 `core/utils/` 移动 |
| `YLib-core/src/main/java/cn/yvmou/ylib/enums/ServerType.java` | 新建或从 common 移动 |

### 平台实现文件（YLib-platform）

| 文件 | 操作 |
|------|------|
| `YLib-platform/spigot/src/main/java/cn/yvmou/ylib/platform/spigot/SpigotScheduler.java` | 从 `platform/spigot/` 移动 |
| `YLib-platform/spigot/src/main/java/cn/yvmou/ylib/platform/spigot/SpigotTask.java` | 从 `platform/spigot/` 移动 |
| `YLib-platform/paper/src/main/java/cn/yvmou/ylib/platform/paper/PaperScheduler.java` | 从 `platform/paper/` 移动 |
| `YLib-platform/paper/src/main/java/cn/yvmou/ylib/platform/paper/PaperTask.java` | 从 `platform/paper/` 移动 |
| `YLib-platform/folia/src/main/java/cn/yvmou/ylib/platform/folia/FoliaScheduler.java` | 从 `platform/folia/` 移动 |
| `YLib-platform/folia/src/main/java/cn/yvmou/ylib/platform/folia/FoliaTask.java` | 从 `platform/folia/` 移动 |

### 插件模式文件（YLib-plugin）

| 文件 | 操作 |
|------|------|
| `YLib-plugin/src/main/java/cn/yvmou/ylib/plugin/YLibPlugin.java` | 新建 |
| `YLib-plugin/src/main/java/cn/yvmou/ylib/plugin/internal/YLibPluginProvider.java` | 新建 |
| `YLib-plugin/src/main/resources/plugin.yml` | 新建 |
| `YLib-plugin/src/main/resources/META-INF/services/cn.yvmou.ylib.YLibProvider` | 新建 |

### 待删除文件

| 文件 |
|------|
| `core/src/main/java/cn/yvmou/ylib/YLib.java` |
| `core/src/main/java/cn/yvmou/ylib/api/` (整个目录) |
| `common/` |
| `platform/` (将被 YLib-platform 替代) |

---

## 任务分解

### 阶段一：创建新模块结构

#### Task 1: 创建 settings.gradle.kts 新结构

**Files:**
- Modify: `YLib/settings.gradle.kts`

- [ ] **Step 1: 更新 settings.gradle.kts**

```kotlin
rootProject.name = "YLib"

include("YLib-api")
include("YLib-core")
include("YLib-platform")
include("YLib-platform:spigot")
include("YLib-platform:paper")
include("YLib-platform:folia")
include("YLib-plugin")
include("example")
```

#### Task 2: 创建 YLib-api 模块 build.gradle.kts

**Files:**
- Create: `YLib/YLib-api/build.gradle.kts`
- Create: `YLib/YLib-api/src/main/java/cn/yvmou/ylib/api/` 目录结构

- [ ] **Step 1: 创建 build.gradle.kts**

```kotlin
plugins {
    `java-library`
}

dependencies {
    compileOnly("org.jetbrains:annotations:23.0.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
```

#### Task 3: 创建 YLib-core 模块 build.gradle.kts

**Files:**
- Create: `YLib/YLib-core/build.gradle.kts`

- [ ] **Step 1: 创建 build.gradle.kts**

```kotlin
plugins {
    `java-library`
}

dependencies {
    api(project(":YLib-api"))
    
    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:23.0.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
```

#### Task 4: 创建 YLib-platform 模块 build.gradle.kts

**Files:**
- Create: `YLib/YLib-platform/build.gradle.kts`

- [ ] **Step 1: 创建 build.gradle.kts**

```kotlin
plugins {
    `java-library`
}

dependencies {
    api(project(":YLib-api"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
```

#### Task 5: 创建 YLib-platform 子模块 build.gradle.kts

**Files:**
- Create: `YLib/YLib-platform/spigot/build.gradle.kts`
- Create: `YLib/YLib-platform/paper/build.gradle.kts`
- Create: `YLib/YLib-platform/folia/build.gradle.kts`

- [ ] **Step 1: 创建 spigot build.gradle.kts**

```kotlin
plugins {
    `java-library`
}

dependencies {
    api(project(":YLib-platform"))
    implementation(project(":YLib-core"))
    
    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
```

- [ ] **Step 2: 创建 paper build.gradle.kts**

```kotlin
plugins {
    `java-library`
}

dependencies {
    api(project(":YLib-platform"))
    implementation(project(":YLib-core"))
    
    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
```

- [ ] **Step 3: 创建 folia build.gradle.kts**

```kotlin
plugins {
    `java-library`
}

dependencies {
    api(project(":YLib-platform"))
    implementation(project(":YLib-core"))
    
    compileOnly("dev.folia:folia-api:1.19.4-R0.1-SNAPSHOT")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
```

#### Task 6: 创建 YLib-plugin 模块 build.gradle.kts

**Files:**
- Create: `YLib/YLib-plugin/build.gradle.kts`

- [ ] **Step 1: 创建 build.gradle.kts**

```kotlin
plugins {
    `java-library`
    id("com.gradleup.shadow")
}

dependencies {
    api(project(":YLib-api"))
    implementation(project(":YLib-core"))
    implementation(project(":YLib-platform"))
    implementation(project(":YLib-platform:spigot"))
    implementation(project(":YLib-platform:paper"))
    implementation(project(":YLib-platform:folia"))
    
    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:23.0.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.shadowJar {
    archiveClassifier.set("")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
```

---

### 阶段二：移动接口文件到 YLib-api

#### Task 7: 移动 scheduler 接口

**Files:**
- Create: `YLib/YLib-api/src/main/java/cn/yvmou/ylib/api/scheduler/UniversalScheduler.java`
- Create: `YLib/YLib-api/src/main/java/cn/yvmou/ylib/api/scheduler/UniversalTask.java`
- Create: `YLib/YLib-api/src/main/java/cn/yvmou/ylib/api/scheduler/UniversalRunnable.java`
- Delete: `core/src/main/java/cn/yvmou/ylib/api/scheduler/` (after verification)

- [ ] **Step 1: 创建 UniversalScheduler.java**

```java
package cn.yvmou.ylib.api.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface UniversalScheduler {

    boolean isFolia();

    @NotNull
    UniversalTask runTask(@NotNull Runnable runnable);

    @NotNull
    UniversalTask runTask(Plugin plugin, @NotNull Runnable runnable);

    @NotNull
    UniversalTask runTask(Location location, @NotNull Runnable runnable);

    @NotNull
    UniversalTask runTask(Plugin plugin, Location location, @NotNull Runnable runnable);

    @NotNull
    UniversalTask runTask(Entity entity, @NotNull Runnable runnable);

    @NotNull
    UniversalTask runTask(Plugin plugin, Entity entity, @NotNull Runnable runnable);

    @NotNull
    UniversalTask runLater(@NotNull Runnable runnable, long delay);

    @NotNull
    UniversalTask runLater(Plugin plugin, @NotNull Runnable runnable, long delay);

    @NotNull
    UniversalTask runLater(Location location, @NotNull Runnable runnable, long delay);

    @NotNull
    UniversalTask runLater(Plugin plugin, Location location, @NotNull Runnable runnable, long delay);

    @NotNull
    UniversalTask runLater(Entity entity, @NotNull Runnable runnable, long delay);

    @NotNull
    UniversalTask runLater(Plugin plugin, Entity entity, @NotNull Runnable runnable, long delay);

    @NotNull
    UniversalTask runTimer(@NotNull Runnable runnable, long delay, long period);

    @NotNull
    UniversalTask runTimer(Plugin plugin, @NotNull Runnable runnable, long delay, long period);

    @NotNull
    UniversalTask runTimer(Location location, @NotNull Runnable runnable, long delay, long period);

    @NotNull
    UniversalTask runTimer(Plugin plugin, Location location, @NotNull Runnable runnable, long delay, long period);

    @NotNull
    UniversalTask runTimer(Entity entity, @NotNull Runnable runnable, long delay, @Nullable Runnable retired, long period);

    @NotNull
    UniversalTask runTimer(Plugin plugin, Entity entity, @NotNull Runnable runnable, long delay, @Nullable Runnable retired, long period);

    @NotNull
    UniversalTask runAsync(@NotNull Runnable runnable);

    @NotNull
    UniversalTask runAsync(Plugin plugin, @NotNull Runnable runnable);

    @NotNull
    UniversalTask runLaterAsync(@NotNull Runnable runnable, long delay);

    @NotNull
    UniversalTask runLaterAsync(Plugin plugin, @NotNull Runnable runnable, long delay);

    @NotNull
    UniversalTask runTimerAsync(@NotNull Runnable runnable, long delay, long period);

    @NotNull
    UniversalTask runTimerAsync(Plugin plugin, @NotNull Runnable runnable, long delay, long period);

    void cancelAllTasks(Plugin plugin);

    void cancelTask(UniversalTask universalTask);

    void teleportAsync(Entity entity, Location location);
}
```

- [ ] **Step 2: 创建 UniversalTask.java**

```java
package cn.yvmou.ylib.api.scheduler;

public interface UniversalTask {
    void cancel();
    boolean isCancelled();
}
```

- [ ] **Step 3: 创建 UniversalRunnable.java**

```java
package cn.yvmou.ylib.api.scheduler;

@FunctionalInterface
public interface UniversalRunnable extends Runnable {
}
```

#### Task 8: 移动其他接口

**Files:**
- Create: `YLib/YLib-api/src/main/java/cn/yvmou/ylib/api/logger/Logger.java`
- Create: `YLib/YLib-api/src/main/java/cn/yvmou/ylib/api/command/CommandManager.java`
- Create: `YLib/YLib-api/src/main/java/cn/yvmou/ylib/api/config/ConfigurationManager.java`
- Create: `YLib/YLib-api/src/main/java/cn/yvmou/ylib/api/config/ConfigValue.java`
- Create: `YLib/YLib-api/src/main/java/cn/yvmou/ylib/api/config/AutoConfiguration.java`
- Create: `YLib/YLib-api/src/main/java/cn/yvmou/ylib/api/config/ConfigurationValidationResult.java`

(接口内容从现有 core 模块移动，保持不变)

---

### 阶段三：创建核心实现

#### Task 9: 创建 YLib 主入口类

**Files:**
- Create: `YLib/YLib-core/src/main/java/cn/yvmou/ylib/YLib.java`

- [ ] **Step 1: 创建 YLib.java**

```java
package cn.yvmou.ylib;

import cn.yvmou.ylib.api.command.CommandManager;
import cn.yvmou.ylib.api.config.ConfigurationManager;
import cn.yvmou.ylib.api.logger.Logger;
import cn.yvmou.ylib.api.scheduler.UniversalScheduler;
import cn.yvmou.ylib.internal.PlatformDetector;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class YLib {
    private static volatile YLib INSTANCE;
    
    private final JavaPlugin plugin;
    private final UniversalScheduler scheduler;
    private final Logger logger;
    private final CommandManager commandManager;
    private final ConfigurationManager configurationManager;

    private YLib(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = PlatformDetector.createScheduler(plugin);
        this.logger = new cn.yvmou.ylib.logger.LoggerImpl(plugin);
        this.commandManager = new cn.yvmou.ylib.command.CommandManagerImpl(plugin, logger);
        this.configurationManager = new cn.yvmou.ylib.config.ConfigurationManagerImpl(plugin, logger);
    }

    @NotNull
    public JavaPlugin getPlugin() {
        return plugin;
    }

    @NotNull
    public UniversalScheduler getScheduler() {
        return scheduler;
    }

    @NotNull
    public Logger getLogger() {
        return logger;
    }

    @NotNull
    public CommandManager getCommandManager() {
        return commandManager;
    }

    @NotNull
    public ConfigurationManager getConfigurationManager() {
        return configurationManager;
    }

    public static void init(@NotNull JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin cannot be null");
        }
        if (INSTANCE != null) {
            throw new IllegalStateException("YLib already initialized");
        }
        synchronized (YLib.class) {
            if (INSTANCE == null) {
                INSTANCE = new YLib(plugin);
            }
        }
    }

    @NotNull
    public static UniversalScheduler getScheduler() {
        checkInitialized();
        return INSTANCE.scheduler;
    }

    @NotNull
    public static Logger getLogger() {
        checkInitialized();
        return INSTANCE.logger;
    }

    @NotNull
    public static CommandManager getCommandManager() {
        checkInitialized();
        return INSTANCE.commandManager;
    }

    @NotNull
    public static ConfigurationManager getConfigurationManager() {
        checkInitialized();
        return INSTANCE.configurationManager;
    }

    public static boolean isInitialized() {
        return INSTANCE != null;
    }

    private static void checkInitialized() {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                "YLib not initialized. Call YLib.init(plugin) first."
            );
        }
    }
}
```

#### Task 10: 创建 PlatformDetector

**Files:**
- Create: `YLib/YLib-core/src/main/java/cn/yvmou/ylib/internal/PlatformDetector.java`

- [ ] **Step 1: 创建 PlatformDetector.java**

```java
package cn.yvmou.ylib.internal;

import cn.yvmou.ylib.api.scheduler.UniversalScheduler;
import cn.yvmou.ylib.enums.ServerType;
import org.bukkit.plugin.Plugin;

public class PlatformDetector {
    
    public static UniversalScheduler createScheduler(Plugin plugin) {
        ServerType type = ServerType.detect();
        switch (type) {
            case FOLIA:
                return createFoliaScheduler(plugin);
            case PAPER:
                return createPaperScheduler(plugin);
            case SPIGOT:
            default:
                return createSpigotScheduler(plugin);
        }
    }
    
    private static UniversalScheduler createSpigotScheduler(Plugin plugin) {
        try {
            Class.forName("cn.yvmou.ylib.platform.spigot.SpigotScheduler");
            return new cn.yvmou.ylib.platform.spigot.SpigotScheduler(plugin);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SpigotScheduler not found on classpath", e);
        }
    }
    
    private static UniversalScheduler createPaperScheduler(Plugin plugin) {
        try {
            Class.forName("cn.yvmou.ylib.platform.paper.PaperScheduler");
            return new cn.yvmou.ylib.platform.paper.PaperScheduler(plugin);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("PaperScheduler not found on classpath", e);
        }
    }
    
    private static UniversalScheduler createFoliaScheduler(Plugin plugin) {
        try {
            Class.forName("cn.yvmou.ylib.platform.folia.FoliaScheduler");
            return new cn.yvmou.ylib.platform.folia.FoliaScheduler(plugin);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("FoliaScheduler not found on classpath", e);
        }
    }
}
```

#### Task 11: 创建 ServerType 枚举

**Files:**
- Create: `YLib/YLib-core/src/main/java/cn/yvmou/ylib/enums/ServerType.java`

- [ ] **Step 1: 创建 ServerType.java**

```java
package cn.yvmou.ylib.enums;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum ServerType {

    FOLIA("Folia", "io.papermc.paper.threadedregions.RegionizedServer"),
    PAPER("Paper", "com.destroystokyo.paper.PaperConfig"),
    SPIGOT("Spigot", "org.spigotmc.SpigotConfig"),
    UNKNOWN("Unknown", null);

    private final String displayName;
    private final String detectionClass;

    ServerType(@NotNull String displayName, @Nullable String detectionClass) {
        this.displayName = displayName;
        this.detectionClass = detectionClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDetectionClass() {
        return detectionClass;
    }

    @NotNull
    public static ServerType detect() {
        for (ServerType type : values()) {
            if (type.detectionClass != null) {
                try {
                    Class.forName(type.detectionClass);
                    return type;
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
        return UNKNOWN;
    }

    @Override
    public String toString() {
        if (this == UNKNOWN) {
            return "Unknown Server Type";
        } else if (this == FOLIA) {
            return "Folia";
        } else if (this == PAPER) {
            return "Paper";
        } else {
            return "Spigot";
        }
    }
}
```

---

### 阶段四：移动现有实现到 YLib-core

#### Task 12: 移动 Logger 实现

**Files:**
- Create: `YLib/YLib-core/src/main/java/cn/yvmou/ylib/logger/LoggerImpl.java`
- Delete: `core/src/main/java/cn/yvmou/ylib/logger/` (after verification)

#### Task 13: 移动 Command 实现

**Files:**
- Create: `YLib/YLib-core/src/main/java/cn/yvmou/ylib/command/*` (除 api 包外)
- Delete: `core/src/main/java/cn/yvmou/ylib/command/` (after verification)

#### Task 14: 移动 Config 实现

**Files:**
- Create: `YLib/YLib-core/src/main/java/cn/yvmou/ylib/config/*` (除 api 包外)
- Delete: `core/src/main/java/cn/yvmou/ylib/config/` (after verification)

#### Task 15: 移动 Utils

**Files:**
- Create: `YLib/YLib-core/src/main/java/cn/yvmou/ylib/utils/*`
- Delete: `core/src/main/java/cn/yvmou/ylib/utils/` (after verification)

---

### 阶段五：创建平台实现到 YLib-platform

#### Task 16: 移动 Spigot 调度器

**Files:**
- Create: `YLib/YLib-platform/spigot/src/main/java/cn/yvmou/ylib/platform/spigot/SpigotScheduler.java`
- Create: `YLib/YLib-platform/spigot/src/main/java/cn/yvmou/ylib/platform/spigot/SpigotTask.java`
- Delete: `platform/spigot/src/main/java/` (after verification)

#### Task 17: 移动 Paper 调度器

**Files:**
- Create: `YLib/YLib-platform/paper/src/main/java/cn/yvmou/ylib/platform/paper/PaperScheduler.java`
- Create: `YLib/YLib-platform/paper/src/main/java/cn/yvmou/ylib/platform/paper/PaperTask.java`
- Delete: `platform/paper/src/main/java/` (after verification)

#### Task 18: 移动 Folia 调度器

**Files:**
- Create: `YLib/YLib-platform/folia/src/main/java/cn/yvmou/ylib/platform/folia/FoliaScheduler.java`
- Create: `YLib/YLib-platform/folia/src/main/java/cn/yvmou/ylib/platform/folia/FoliaTask.java`
- Delete: `platform/folia/src/main/java/` (after verification)

---

### 阶段六：创建插件模式入口 YLib-plugin

#### Task 19: 创建 YLibProvider SPI 接口

**Files:**
- Create: `YLib/YLib-core/src/main/java/cn/yvmou/ylib/YLibProvider.java`

- [ ] **Step 1: 创建 YLibProvider.java**

```java
package cn.yvmou.ylib;

import org.bukkit.plugin.java.JavaPlugin;

public interface YLibProvider {
    void initialize(JavaPlugin plugin);
}
```

#### Task 20: 创建 YLibPluginProvider

**Files:**
- Create: `YLib/YLib-plugin/src/main/java/cn/yvmou/ylib/plugin/internal/YLibPluginProvider.java`

- [ ] **Step 1: 创建 YLibPluginProvider.java**

```java
package cn.yvmou.ylib.plugin.internal;

import cn.yvmou.ylib.YLib;
import cn.yvmou.ylib.YLibProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class YLibPluginProvider implements YLibProvider {
    @Override
    public void initialize(JavaPlugin plugin) {
        YLib.init(plugin);
    }
}
```

#### Task 21: 创建 YLibPlugin 主类

**Files:**
- Create: `YLib/YLib-plugin/src/main/java/cn/yvmou/ylib/plugin/YLibPlugin.java`

- [ ] **Step 1: 创建 YLibPlugin.java**

```java
package cn.yvmou.ylib.plugin;

import cn.yvmou.ylib.YLib;
import org.bukkit.plugin.java.JavaPlugin;

public class YLibPlugin extends JavaPlugin {
    
    @Override
    public void onLoad() {
        YLib.init(this);
    }
    
    @Override
    public void onEnable() {
        getLogger().info("YLib enabled!");
    }
}
```

#### Task 22: 创建 plugin.yml

**Files:**
- Create: `YLib/YLib-plugin/src/main/resources/plugin.yml`

- [ ] **Step 1: 创建 plugin.yml**

```yaml
name: YLib
version: '${version}'
main: cn.yvmou.ylib.plugin.YLibPlugin
api-version: '1.19'
description: A Minecraft library for Folia servers (Spigot/Paper compatible)
author: yvmouX
website: https://github.com/yvmouX/YLib
```

#### Task 23: 创建 SPI 注册文件

**Files:**
- Create: `YLib/YLib-plugin/src/main/resources/META-INF/services/cn.yvmou.ylib.YLibProvider`

- [ ] **Step 1: 创建 SPI 文件**

```
cn.yvmou.ylib.plugin.internal.YLibPluginProvider
```

---

### 阶段七：清理旧文件

#### Task 24: 删除旧模块

**Files:**
- Delete: `core/` (YLib-core 替代)
- Delete: `common/` (已合并)
- Delete: `platform/` (YLib-platform 替代)

#### Task 25: 更新根 build.gradle.kts

**Files:**
- Modify: `YLib/build.gradle.kts`

- [ ] **Step 1: 更新 build.gradle.kts**

```kotlin
plugins {
    `java-library`
    `maven-publish`
}

allprojects {
    apply(plugin = "java-library")

    group = "com.github.yvmoux"
    version = "1.0.0-alpha.9"

    repositories {
        mavenCentral()
        maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
        maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/public/") }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    api(project(":YLib-api"))
    api(project(":YLib-core"))
    api(project(":YLib-platform"))
    api(project(":YLib-platform:spigot"))
    api(project(":YLib-platform:paper"))
    api(project(":YLib-platform:folia"))
    api(project(":YLib-plugin"))
}
```

---

### 阶段八：测试验证

#### Task 26: 编译验证

- [ ] **Step 1: 运行 gradle build**

Run: `./gradlew clean build`
Expected: 所有模块编译成功

#### Task 27: 库模式测试

- [ ] **Step 1: 在 example 插件中测试 shade**

```java
// example 插件 onLoad()
YLib.init(this);

// 测试静态方法
YLib.getLogger().info("Hello from shaded YLib!");
YLib.getScheduler().runTask(() -> {
    // do something
});
```

#### Task 28: 插件模式测试

- [ ] **Step 1: 单独构建 YLib-plugin jar**
- [ ] **Step 2: 安装到测试服务器**
- [ ] **Step 3: 测试依赖 YLib 的插件能否正常工作**
