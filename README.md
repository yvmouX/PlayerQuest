# 开发文档

---

## 快速跳转

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
# 跟踪子模块配置的分支（当前为 dev）
git submodule update --remote YLib
# 或进入子模块手动切换分支
git -C YLib checkout dev && git -C YLib pull
```

当前 YLib 子模块跟踪 `dev` 分支（`1.0.0-beta10`）。
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
  注意仓库根 `.gitignore` 的 `*.bat` 规则会忽略 `gradlew.bat`，需 `git add -f gradlew.bat` 才能纳入版本控制。

### 注意事项

- **改动 YLib 源码后可能需重启构建**：复合构建对已解析依赖做缓存，
  若改动未生效，先执行 `.\gradlew.bat --stop` 或清理构建缓存再重新构建。
- **重定位**：主工程把 `cn.yvmou.ylib` 重定位到 `com.playerPlugin.playerTaskX.lib.ylib`。
  YLib 通过 `ServiceLoader` 定位服务实现，`build.gradle.kts` 中的
  `mergeServiceFiles()` 负责同步重写服务文件路径与内容；
  YLib 侧另有「重定位安全回退」，两者互为保险。

---

### 储存

- 玩家进度
  - SQLite (默认)
  - MySQL
  - YAML
  - JSON
- 任务
  - 主体
    - YAML(默认)
    - JSON
    - SQLite
    - MySQL
  - 奖励
    - YAML(默认)
    - JSON
    - SQLite
    - MySQL
  - 目标
    - YAML(默认)
    - JSON
    - SQLite
    - MySQL
