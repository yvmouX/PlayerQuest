# YLib 重构设计文档

## 1. 概述

### 1.1 项目背景

YLib 是一个 Minecraft 服务器插件库，提供调度器、日志、命令系统和配置管理等功能。当前设计存在以下问题：
- API 和实现混在同一个模块
- 双模式（库模式/插件模式）支持不明确
- 静态便捷方法与实例方法职责不清

### 1.2 重构目标

- 支持两种使用模式：
  1. **库模式**：其他插件将 YLib shade 进去，无需前置插件
  2. **插件模式**：YLib 作为独立插件安装，其他插件依赖它
- 提供统一的 API 接口，两种模式均可使用
- 符合 Java 库设计最佳实践

## 2. 模块结构

```
YLib/
├── YLib-api/              # 接口模块（仅接口，无实现依赖）
│   └── cn.yvmou.ylib.api.*
│       ├── scheduler/
│       │   ├── UniversalScheduler.java
│       │   ├── UniversalTask.java
│       │   └── UniversalRunnable.java
│       ├── logger/
│       │   └── Logger.java
│       ├── command/
│       │   └── CommandManager.java
│       └── config/
│           ├── ConfigurationManager.java
│           ├── ConfigValue.java
│           ├── AutoConfiguration.java
│           └── ConfigurationValidationResult.java
│
├── YLib-core/             # 核心实现（依赖 YLib-api）
│   ├── cn.yvmou.ylib.YLib              # 主入口类
│   ├── cn.yvmou.ylib.internal.*
│   ├── cn.yvmou.ylib.logger.*           # LoggerImpl
│   ├── cn.yvmou.ylib.command.*          # 命令系统实现
│   ├── cn.yvmou.ylib.config.*           # 配置系统实现
│   └── cn.yvmou.ylib.utils.*            # 工具类
│
├── YLib-platform/        # 平台实现（依赖 YLib-api）
│   ├── cn.yvmou.ylib.platform.spigot.*
│   ├── cn.yvmou.ylib.platform.paper.*
│   └── cn.yvmou.ylib.platform.folia.*
│
└── YLib-plugin/          # 插件模式入口（独立安装时使用）
    ├── cn.yvmou.ylib.plugin.YLibPlugin  # Bukkit 插件入口
    └── cn.yvmou.ylib.plugin.internal.*  # SPI 实现
```

### 2.1 模块依赖关系

```
YLib-plugin
    └── YLib-core
            └── YLib-api

YLib-platform (spigot/paper/folia)
    └── YLib-api

其他插件（库模式）
    ├── shade YLib-core + YLib-platform + YLib-api
    └── 调用 YLib.init()

其他插件（插件模式）
    └── 依赖 YLib-api + YLib-plugin
```

## 3. 核心设计

### 3.1 YLib 主入口类

```java
package cn.yvmou.ylib;

public class YLib {
    private static volatile YLib INSTANCE;
    private final JavaPlugin plugin;
    private final UniversalScheduler scheduler;
    private final Logger logger;
    private final CommandManager commandManager;
    private final ConfigurationManager configurationManager;

    private YLib(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = PlatformDetector.createScheduler(plugin);
        this.logger = new LoggerImpl(plugin);
        this.commandManager = new CommandManagerImpl(plugin, logger);
        this.configurationManager = new ConfigurationManagerImpl(plugin, logger);
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public UniversalScheduler getScheduler() {
        return scheduler;
    }

    public Logger getLogger() {
        return logger;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public ConfigurationManager getConfigurationManager() {
        return configurationManager;
    }

    // ===== 库模式初始化 =====
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

    // ===== 静态便捷方法 =====
    public static UniversalScheduler getScheduler() {
        checkInitialized();
        return INSTANCE.scheduler;
    }

    public static Logger getLogger() {
        checkInitialized();
        return INSTANCE.logger;
    }

    public static CommandManager getCommandManager() {
        checkInitialized();
        return INSTANCE.commandManager;
    }

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

### 3.2 平台检测

```java
package cn.yvmou.ylib.internal;

public class PlatformDetector {
    public static UniversalScheduler createScheduler(Plugin plugin) {
        ServerType type = ServerType.detect();
        switch (type) {
            case FOLIA:
                return new FoliaScheduler(plugin);
            case PAPER:
                return new PaperScheduler(plugin);
            case SPIGOT:
            default:
                return new SpigotScheduler(plugin);
        }
    }
}
```

### 3.3 SPI 机制

插件模式通过 Java SPI 发现实现：

`YLib-plugin/src/main/resources/META-INF/services/cn.yvmou.ylib.YLibProvider`:
```
cn.yvmou.ylib.plugin.internal.YLibPluginProvider
```

```java
public interface YLibProvider {
    void initialize(JavaPlugin plugin);
}
```

```java
public class YLibPluginProvider implements YLibProvider {
    @Override
    public void initialize(JavaPlugin plugin) {
        YLib.init(plugin);
    }
}
```

## 4. 使用方式

### 4.1 库模式（推荐用于独立插件）

```java
public class MyPlugin extends JavaPlugin {
    @Override
    public void onLoad() {
        YLib.init(this);
    }

    @Override
    public void onEnable() {
        YLib.getLogger().info("Plugin enabled!");
        YLib.getScheduler().runTask(() -> {
            // do something
        });
        YLib.getCommandManager().register(new MyCommand());
    }
}
```

### 4.2 插件模式

**YLib 插件安装到服务器：**
- YLib 独立安装
- 通过 `YLibProvider` SPI 注册

**依赖 YLib 的插件：**

`plugin.yml`:
```yaml
name: MyPlugin
version: 1.0.0
main: com.example.MyPlugin
depend: [YLib]
```

```java
public class MyPlugin extends JavaPlugin {
    @Override
    public void onLoad() {
        // 库模式相同的初始化方式
        YLib.init(this);
    }
}
```

## 5. Gradle 构建配置

### 5.1 模块配置

```kotlin
// YLib-api/build.gradle.kts
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

```kotlin
// YLib-core/build.gradle.kts
plugins {
    `java-library`
}

dependencies {
    api(project(":YLib-api"))
    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
```

### 5.2 Shade 配置（用于库模式）

```kotlin
// 依赖 YLib 的插件 build.gradle.kts
plugins {
    id("com.gradleup.shadow") version "9.3.0"
}

dependencies {
    implementation("com.github.yvmoux:YLib:1.0.0")
}

shadowJar {
    relocate("cn.yvmou.ylib", "shadowed.cn.yvmou.ylib")
    archiveClassifier.set("")
}
```

## 6. 文件清理

重构后需要删除/移动以下文件：

| 原路径 | 新路径 |
|--------|--------|
| `core/src/main/java/cn/yvmou/ylib/YLib.java` | 拆分：接口→api，实现→core |
| `core/src/main/java/cn/yvmou/ylib/api/` | 移动到 `api/` 模块 |
| `core/src/main/java/cn/yvmou/ylib/logger/` | 保留在 `core/` |
| `core/src/main/java/cn/yvmou/ylib/command/` | 保留在 `core/` |
| `core/src/main/java/cn/yvmou/ylib/config/` | 保留在 `core/` |
| `common/` | 合并到 `core/` 或删除 |
| `platform/spigot/`, `platform/paper/`, `platform/folia/` | 移动到 `YLib-platform/` |

## 7. 迁移策略

1. **创建新模块结构**
2. **移动代码到新模块**
3. **更新依赖关系**
4. **测试库模式**
5. **测试插件模式**
6. **发布新版本**

## 8. 风险与注意事项

- **SPI 兼容性**：确保 `META-INF/services` 文件正确打包
- **Shade 冲突**：用户 shade 时需要注意 relocaton
- **版本兼容性**：保持 API 接口向后兼容
