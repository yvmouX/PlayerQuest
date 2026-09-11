



# PlayerTaskX

Minecraft 任务插件（Spigot / Paper / Folia / Canvas，1.21.x，Java 21）。
「任务 = 多个目标 + 多个奖励」，支持网页编辑器、游戏内 GUI、多语言、PlaceholderAPI。

> 设计与模块划分见 [`ARCHITECTURE.md`](ARCHITECTURE.md)
>
> 面向使用者的文档在 `docs/`（docsify）。

---

## 1. 环境与命令

在 Windows 上开发，用 `pwsh`。Gradle 用仓库自带的 wrapper：

```powershell
.\gradlew.bat clean build            # 完整构建（含前端）
.\gradlew.bat :core:compileJava -x :core:frontendBuild   # 只编译后端，跳过前端，几秒钟
.\gradlew.bat :core:test             # 跑测试
.\gradlew.bat :core:test --tests '*DailyServiceTest*'    # 跑单个测试类
.\start-folia.ps1                    # 构建 → 复制产物到 run/plugins → 启动测试服
```

`start-folia.ps1` 的开关：`-Clean`（先 clean）、`-SkipBuild`（只启动）、`-Foreground`（前台运行）。

**已知陷阱：**

- `start-folia.ps1` 必须保留 **UTF-8 BOM**。PowerShell 5.1 无 BOM 时按 ANSI 读取，
  中文会变乱码并导致脚本解析报错。改完这个文件务必确认 BOM 还在。
- `npm run build`（在 `task-editor-vue/`）会跑 `vue-tsc` 类型检查再 `vite build`。
  **Gradle 的 `:core:frontendBuild` 会调用它，因此类型错误会让整个 `build` 失败。**
- 前端产物输出到 `core/src/main/resources/web/`（vite 的 `outDir` 就指在那里），
  **不是 `dist/`**，且该目录已被 gitignore（构建时生成，不要提交）。
- 服务端控制台的中文乱码通常只是 Windows 终端显示问题，日志与落盘内容是正确 UTF-8。
- 测试服默认端口：游戏 25565，网页编辑器 8080（见 `run/plugins/playerTaskX/config.yml`）。
  配置里的 `editor.token` 为空时不校验令牌。
- 停止测试服：RCON 未启用，用 `Get-CimInstance Win32_Process -Filter "Name='java.exe'"` 找到
  folia 进程后 `Stop-Process`。**不要在服务器运行时用外部工具改它的 SQLite 文件**——
  我踩过：外部连接执行的 DDL 会被 SQLite 的 WAL 回滚，得出完全错误的结论。

---

## 2. 模块结构

```
api/                模型与三个扩展点接口（ObjectiveType / RewardType / ProgressContext）
core/               实现：引擎、存储、GUI、命令、网页编辑器后端
task-editor-vue/    网页编辑器前端（Vue 3 + TS + Vite）
YLib/               git 子模块，位于 dev 分支；通过 settings.gradle.kts 的 includeBuild 参与构建
docs/               docsify 文档站
```

**`api/` 必须是 Java 8 源码级**：YLib 的 api 模块是 Java 8，不能使用 record、
`var`、`List.of()` 之类的新语法。`core/` 才可以用 Java 21。

---

## 3. 架构上最容易搞错的点

### 三个扩展点与 schema 驱动

```java
public interface ObjectiveType {
    String id();
    String displayName();
    List<ConfigField> schema();   // ← 网页编辑器与 GUI 的表单都据此生成
    int match(ProgressContext context, Map<String, Object> properties);
}
```

目标与奖励是**数据而非代码**（`QuestObjective(type, properties)`），
编辑器与 GUI 里**不允许**出现针对具体类型 id 的硬编码分支。

**`ConfigField` 的类型必须如实声明**，这决定编辑器给什么控件：

| 类型 | 控件 |
|---|---|
| `MATERIAL` | 材质选择器（支持逗号分隔多值） |
| `ENTITY` | 实体选择器 |
| `TARGET` | 方块或实体选择器 |
| `STRING` / `INTEGER` / `DECIMAL` / `BOOLEAN` / `ENUM` | 常规控件 |

把「材质名」写成 `STRING` 不会编译报错、也不会让任何断言变红，只会让管理员面对一个
纯文本框、被迫去查 Bukkit 枚举名。同理，`hint` 里写了「留空表示任意」就必须用
`optionalMaterial` / `optionalEntity` / `optionalBlockOrEntity` / `optionalText`
——否则编辑器会显示与实际校验不符的必填星号。
（`ObjectiveFieldTypeConsistencyTest` 会拦住这两类错误。）

### 文本渲染只有一条正确路径

先把 `&` / `§` 颜色码翻译成 MiniMessage 标签，**再渲染一次**：

- MiniMessage 遇到原始 `§` 会抛异常，所以不能直接把它喂给 MiniMessage；
- `LegacyComponentSerializer` 会把 `§` 原样透传，所以也不能指望它做转换。

**actionbar 是特例**：Paper 的 `sendActionBar(String)` 重载**不解析 MiniMessage**，
必须传 `§` 传统颜色码（`TextUpgrader.toLegacy`）。这是唯一需要绕路的地方。

### 货币

`CurrencyType`（MONEY / POINTS / EXP），按配置里的**有序列表**依次探测可用性：
没装 Vault 就用点券，都没装就兜底到经验（经验永远可用）。
**不存在「任务币」**——曾经有过，已移除，不要复活它。

### 存储

SQLite（默认，开箱即用）与 MySQL 共用一套仓储代码，方言差异集中在 `Dialect`。
`player_coin` 之类历史表已删除，新环境只建 6 张表。

---

## 4. 网页编辑器

Javalin 6 提供 REST + 静态资源（`/` + `/assets/{file}`），前端用 hash 路由。

- **素材目录**（`/api/catalog`）遍历运行期的 `Material` / `EntityType` 枚举生成，
  因此天然只含当前服务端版本支持的项，**不需要维护版本对照数据**。
  英文名读服务端 jar 里的 `en_us.json`；中文名是内置精选表（中文译名只存在于客户端
  资源里，服务端无从获取），未收录项回退英文名——需要中英双语搜索能力。
- **预设**（`/api/presets`）落在 `plugins/playerTaskX/presets.json`，**不进数据库**：
  引擎不认识预设，它只是编辑器的便利设施。
- 接口已开启 gzip（Javalin 对超过 1500 字节的响应自动压缩）：catalog 从 121KB 压到约 21KB。
- 改动前端后 `:core:frontendBuild` 会自动重建；**不要手工往
  `core/src/main/resources/web/` 里塞东西**。

---

## 5. 协作约定

- 全程用**中文**交流；代码注释也用中文，并写「为什么」而不是复述代码在做什么。
- **简洁优先**。为一个小功能引入 YAML 元数据层、事件总线这类间接层会被否掉。
- **不考虑向后兼容**（项目尚未发布）。不要为「已存在的部署」保留兼容分支或遗留建表语句；
  死代码直接删，而不是留别名。
- **命名不能误导**。曾经 `/ptxa reroll` 实际上扣了玩家的钱，被要求改名：
  玩家刷新（`/ptx refresh`，扣费、消耗次数）与管理员重置（`/ptxa resetdaily`，不扣费）
  是两件事，不要合并或互相别名。
- 危险或不可逆的操作先问，不要自行决定。
- 尽量**用测试钉住行为**，尤其是「不报错的错误」（字段类型、required 与文案矛盾、
  元数据与实际不符）。这类问题编译和普通断言都抓不到。
- 提交信息写清「为什么」以及被否掉的替代方案，不要只写「update xx」。

---

## 6. 验证的真实边界

- 事件 → 进度累加 → 达标发奖的主链路有单元测试，**不依赖服务端**，改动后必须全绿。
- 文本渲染、每日抽取、存储方言、编辑器素材、GUI 图标推导都有测试。
- **无法自行验证的**：真人进服的游玩路径、actionbar/title 的实际显示效果、
  浏览器里的前端交互（环境没有 playwright，只能靠构建 + 类型检查 + SSR 渲染）。
  这些要如实说明「未验证」，不要声称已验证。
- 真机冒烟：`.\start-folia.ps1` + 探测 `http://127.0.0.1:8080/api/stats`。
  最近一次实测结论（Folia 26.1.2-8，1506 材质 / 157 实体）记在 `ARCHITECTURE.md`。
