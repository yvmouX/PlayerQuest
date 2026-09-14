# PlayerTaskX

面向 Spigot / Paper / Folia / Canvas 的**每日任务与自定义任务**插件：
任务 = 多个目标 + 多个奖励，支持网页编辑器、游戏内 GUI、多语言与 PlaceholderAPI 变量。

## 文档

在线文档（docsify，随 `main` 自动发布）：**https://yvmouX.github.io/PlayerTaskX/**

也可以直接看仓库里的 Markdown：

| 文档 | 内容 |
|---|---|
| [使用文档总览](docs/README.md) | 从这里开始 |
| [快速开始](docs/quick-start.md) | 安装与 5 分钟跑通第一个任务 |
| [命令](docs/commands.md) | 玩家命令 / 管理员命令 |
| [配置](docs/configuration.md) | `config.yml` 全部选项 |
| [任务目标](docs/objectives.md) | 15 种目标类型 |
| [任务奖励](docs/rewards.md) | 3 种奖励类型（金币 / 点券 / 命令） |
| [网页编辑器](docs/editor.md) | 浏览器管理界面 |
| [变量](docs/placeholders.md) | PlaceholderAPI 变量 |
| [常见问题](docs/faq.md) | 排查手册 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 架构设计与实现说明（开发者向） |

## 构建

```powershell
.\gradlew.bat build                # 构建 + 跑测试
.\start-folia.ps1                  # 构建 + 部署到 run\plugins + 启动测试服
```

产物为 `build/libs/playerTaskX-<版本>-all.jar`（部署用这个 `-all` 版本）。

---

## 开发文档

### 快速跳转

- [存储](#存储)
- [YLib 子模块](#ylib-子模块)

---

## YLib 子模块

本项目的 `YLib` 是 git 子模块，并在 `settings.gradle.kts` 中通过 `includeBuild("YLib")` 以**复合构建**方式接入：

```kotlin
includeBuild("YLib")
```

这意味着编译与打包使用的始终是**本地 YLib 源码**，而不是 JitPack 上的产物。
（Gradle 的依赖替换按 `group:name` 匹配，`build.gradle.kts` 里的版本号仅在没有 `includeBuild` 时生效，仅作坐标提示。）

### 更新 YLib

```bash
# ⚠️ .gitmodules 里没有 branch 键，--remote 跟的是远端默认分支（HEAD），不是 dev
git submodule update --remote YLib
# 想拿 dev 分支的更新，就进子模块手动切
git -C YLib checkout dev && git -C YLib pull
```

当前 YLib 子模块的**工作区停在 `dev` 分支**（`gradle.properties` 版本 `1.0.0-beta10`），
但这一步是手动切出来的：`.gitmodules` 只写了 `path` 与 `url`、**没有 `branch` 键**，
所以 `git submodule update --remote YLib` 跟的是 YLib 远端的默认分支，**不保证落在 `dev` 上**。

若希望 `--remote` 直接跟随 `dev`，在 `.gitmodules` 里给该子模块补一行 `branch`：

```ini
[submodule "YLib"]
	path = YLib
	url = https://github.com/yvmouX/YLib.git
	branch = dev
```

（改 `.gitmodules` 会改变所有协作者执行 `git submodule update --remote` 的目标分支，
属于 git 工作流层面的决定，因此这里只说明做法，不代改。）

子模块内含一处本地构建修正（见下方「构建接线」），切换分支或 `git pull` 后需确认它仍在，
否则 `:shadowJar` 会因取不到 YLib 聚合产物而失败。

### 构建接线

- **YLib 聚合产物必须暴露为构件**：YLib 根项目没有源码，其可发布产物是聚合各模块的
  `shadowJar`（`jar` 任务被禁用）。`YLib/build.gradle.kts` 中已将其挂到 `api` 配置上，
  否则消费方解析到的 `YLib-<version>.jar` 不会生成，`shadowJar` 会报
  `Cannot expand ZIP ... as it does not exist`。
  ⚠️ 这处修改目前只存在于工作区，需在 YLib 仓库提交后父仓库才能真正锁定。
- **wrapper**：`gradle/wrapper/gradle-wrapper.jar` 原为无效 jar（清单缺少 `Main-Class`），
  已用 `gradle wrapper` 重新生成（wrapper jar 来自本机 Gradle 8.14.4，目标发行版仍为 8.14.3）；
  `gradle-wrapper.properties` 保留腾讯镜像 `mirrors.cloud.tencent.com`。
  仓库根 `.gitignore` 的 `*.bat` 规则已用 `!gradlew.bat` 显式豁免，`gradlew.bat` 正常纳入版本控制。

### 注意事项

- **改动 YLib 源码后可能需重启构建**：复合构建对已解析依赖做缓存，
  若改动未生效，先执行 `.\gradlew.bat --stop` 或清理构建缓存再重新构建。
- **重定位**：主工程把 `cn.yvmou.ylib` 重定位到 `com.playerPlugin.playerTaskX.lib.ylib`。
  YLib 通过 `ServiceLoader` 定位服务实现，`build.gradle.kts` 中的
  `mergeServiceFiles()` 负责同步重写服务文件路径与内容；
  YLib 侧另有「重定位安全回退」，两者互为保险。

---

### 储存

任务定义、预设与玩家数据**都存在同一个数据库**里，由 `storage.type` 一处决定，只有两个后端：

- `SQLITE`：**默认**，零配置、单文件，适合单机与小型服务器
- `MYSQL`：多服共享玩家数据时必须使用

两类数据同库但分表：`quest` / `quest_objective` / `quest_reward` / `preset` 是内容，
`player_quest` / `daily_state` 是玩家状态。

任务是一个整体（目标与奖励是它的一部分），不存在「主体 / 奖励 / 目标」三套独立存储。
**没有 YAML / JSON 文件后端**：YAML 1.1 会把 `target: NO` 解析成布尔、把 `1.20` 解析成浮点；
文件后端则要面对「文件与库哪个是权威」以及每次进度都重写整份文件的问题。
需要 diff 或进版本控制时，用编辑器的整份任务导出 / 导入。
