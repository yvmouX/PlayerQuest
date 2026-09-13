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
- 关于前端产物输出：
  - 产物输出到 `core/src/main/resources/web/`（vite outDir），不是`dist/`；该目录已加入 gitignore，属于构建临时文件，不要提交。
  - `task-editor-vue` 的 `npm run build` 会先跑 `vue-tsc` 类型检查，因此类型错误会让整个 Gradle build 失败。
- 关于源码级别：
  - `api/` 必须是 Java 8 源码级，因为YLib 的 api 模块是 Java 8，不能使用 record、
    `var`、`List.of()` 之类的新语法。`core/` 才可以用 Java 21
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
