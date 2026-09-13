# PlayerTaskX

Minecraft 任务插件（Spigot / Paper / Folia / Canvas，1.21.x，Java 21）。
「任务 = 多个目标 + 多个奖励」，支持网页编辑器、游戏内 GUI、多语言、PlaceholderAPI等

> 基于YLib（在保持代码整洁和效率以及其他的特殊情况下，可以直接修改YLib的源码）
>
> 设计与模块划分见 [`ARCHITECTURE.md`](ARCHITECTURE.md)（在保持代码整洁和效率以及其他的特殊情况下，可以直接修改该文档，保持该文档保持更新）
>
> 面向使用者的文档在 `docs/`（每次改动要修改这个文档，保持更新）。



- 命令：

  - ```powershell
    .\gradlew.bat clean build            # 完整构建（含前端）
    .\gradlew.bat :core:compileJava -x :core:frontendBuild   # 只编译后端，跳过前端，几秒钟
    .\gradlew.bat :core:test             # 跑测试
    .\gradlew.bat :core:test --tests '*DailyServiceTest*'    # 跑单个测试类
    .\start-folia.ps1                    # 构建 → 复制产物到 run/plugins → 启动测试服
    ```

  - `start-folia.ps1` 的开关：`-Clean`（先 clean）、`-SkipBuild`（只启动）、`-Foreground`（前台运行）。
  - `start-folia.ps1` 必须保留 **UTF-8 BOM**：PowerShell 5.1 无 BOM 时按 ANSI 读取，
    中文会乱码并导致脚本解析报错。改完这个文件要确认 BOM 还在。
  - YLib 的测试在子模块里：`cd YLib; ..\gradlew.bat :core:test`。

- 关于服务器关停
  - 可在启动的时候手动开启rcon，开启后需告知开发者
  - 未开 rcon 时：用 `Get-CimInstance Win32_Process -Filter "Name='java.exe'"` 找到 folia 进程后 `Stop-Process`。
  - **不要在服务器运行时用外部工具改它的 SQLite 文件**：外部连接执行的 DDL 会被 SQLite 的 WAL
    回滚，会得出完全错误的结论（踩过）。
- 关于存储（改之前务必先读这段）
  - 数据分两类、**各自独立选后端**：任务定义与预设走 `definitions.type`，玩家数据走 `storage.type`。
    默认是「定义用 JSON 文件 + 玩家数据用 SQLite」，三个后端（JSON / SQLite / MySQL）都支持。
  - 两类的契约**不要合并**：玩家侧需要 `findActiveByPlayer`（在进度热路径上）、聚合与事务，
    定义侧都不需要。合并的代价要么是玩家侧丢索引查询与事务，要么是文件后端被迫实现一个
    键控可查询事务存储（等于用文件重写数据库）。
  - SQLite 与 MySQL **共用同一套 JDBC 实现**，差异全在 `Dialect`——加后端时别写第三套。
  - 文件后端有两条硬要求，改它时务必保持：**写入原子**（临时文件 + 原子改名，绝不原地覆盖）、
    **载入分级容错**（单文件坏了跳过并记文件名，缺 id 跳过，文件名与 id 冲突时以 id 为准）。
  - **定义侧用 JSON 不用 YAML**：YAML 1.1 会把 `target: NO`（合法方块材质名）解析成布尔 false、
    把 `1.20` 解析成浮点。干净解法（YAML 1.2 风格 resolver）在 Jackson 2.15.2 上挂不上去
    （`YAMLFactoryBuilder` 不暴露 resolver）。不要再试图换回 YAML，除非同时解决这一点。
  - **目标结构指纹不要删**：进度按目标下标记录，调换顺序会让旧进度静默错配到别的目标上，
    语法校验查不出来。检测必须放在 `ProgressService.load()`（覆盖全部记录），
    只放热路径会漏掉已完成记录。行为约定是「重置该任务进度 + 记日志」，不是静默错配。
- 关于前端产物输出：
  - 产物输出到 `core/src/main/resources/web/`（vite outDir），不是`dist/`；该目录已加入 gitignore，属于构建临时文件，不要提交。
  - `task-editor-vue` 的 `npm run build` 会先跑 `vue-tsc` 类型检查，因此类型错误会让整个 Gradle build 失败。
- 关于源码级别（**本项目与 YLib 恰好相反，别搞混**）：
  - **本项目的 `api/`、`core/` 都是 Java 21**：根 `build.gradle.kts` 的 `allprojects` 里
    `val targetJavaVersion = 21`，`api/build.gradle.kts` 是空的、不覆盖它，因此 record、
    `var`、`List.of()` 都可以用——`api/.../model/Quest.java`、`QuestObjective`、`Preset`、
    `ConfigField`、`ProgressContext` 本身就是 record。
  - **YLib 的 `api` / `core` 才是 Java 8**（`YLib/build.gradle.kts` 按模块给级别：
    `:platform:canvas|folia|paper` 是 17、根项目是 21、**其余默认 `VERSION_1_8`**）。
    改 YLib 源码时要守它的级别——record / `var` / `List.of()` 不能出现在 YLib 的
    `api`、`core` 里（`cn.yvmou.ylib.command.help.CommandHelp.Entry` 写成普通类就是这个原因）。
  - 这条约束比看上去宽松：Adventure **4.x 全线是 Java 8 字节码**（5.x 才要 Java 21），
    所以 YLib 能在保持 Java 8 的同时使用 MiniMessage。想引入新依赖前先确认它要求的字节码版本。

- 关于文本渲染（容易搞错，先说清楚）
  - 渲染实现在 **YLib**（`cn.yvmou.ylib.text.TextRenderer`），不在本插件里。
    YLib 的 `MessageService` 出口（`raw`/`send`/`prefix`）已经是渲染好的 `§` 色码，
    **不要再包一层**——本插件曾有一个 `LangMessageService` 包装器与自己的 `TextRenderer`，
    已随渲染上移一并删除。
  - 需要渲染语言文件之外的文本（物品名、GUI 标题、进度行）时直接用
    `TextRenderer.render/strip/parse`。
  - `&`、`§`、MiniMessage 标签三种写法可任意混排，但**必须一次性渲染**：
    MiniMessage 遇到 `§` 会整串放弃解析，而 `LegacyComponentSerializer` 只会把 `§`
    原样写回、对 `<yellow>` 当字面量。分段渲染必然出错。
  - 交给 `sendActionBar(String)` / `sendTitle(String)` / `setDisplayName` 的必须是
    `§` 色码：实测 Paper 的 `sendActionBar(String)` 不解析 MiniMessage，会把标签显示给玩家。

- 关于物品译名（编辑器图标列表）
  - **能离线拿到的数据不要走网络**：英文名直接读服务端 jar 自带的
    `assets/minecraft/lang/en_us.json`，版本天然对齐。
  - 中文名服务端**没有**（实测服务端 jar 内 lang 文件只有 en_us.json 一个），
    只能另行获取：优先读 `plugins/playerTaskX/lang/zh_cn.json`（管理员可手动放，
    离线服就这么用），没有则按 `editor.fetch-chinese-names` 决定是否从官方 CDN 下载并缓存。
  - 下载是**尽力而为**的：失败只记日志、不阻断启用、不重试。这一点是刻意的——
    不少服务器在受限网络里，且管理工具会把「插件外连」视为可疑行为。
  - 早期有一份内置手工中文表（约 175 行、材质覆盖仅两成），已删除；不要再引入这类表，
    它必然随版本失效。实测替换后材质与实体中文覆盖均为 100%。

- 协作约定：
  - 全程用**中文**交流；代码注释也用中文，并写「为什么」而不是复述代码在做什么
  - **简洁优先**。尽可能保持代码简洁可读性更好，必要时可以修改YLib源码或者引入外部库。
    反过来，能用一处改动解决的，不要再包一层适配器——包装器属于「必要时」之外。
  - **不考虑向后兼容**（项目尚未发布）。不要为「已存在的部署」保留兼容分支或遗留建表语句；
    死代码直接删，而不是留别名。
  - 危险或不可逆的操作先问，不要自行决定。
  - 尽量**用测试钉住行为**，尤其是「不报错的错误」（字段类型写错、元数据与说明矛盾、
    依赖字节码版本这类编译期与普通断言都抓不到的问题）。
    能力上移到 YLib 的，测试也一并上移，否则每个消费方只能各测各的。
  - 提交信息写清「为什么」以及被否掉的替代方案，不要只写「update xx」
  - 无法自行验证的部分（真人进服、actionbar/title 实际显示、浏览器交互）如实说明「未验证」，
    不要声称已验证。
