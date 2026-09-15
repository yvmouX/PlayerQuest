# PlayerTaskX

Minecraft 任务插件（Spigot / Paper / Folia / Canvas，1.21.x，Java 21）。「任务 = 多个目标 + 多个奖励」，支持游戏内 GUI、多语言、PlaceholderAPI。
任务定义可以放在数据库里（游戏内命令 / GUI 维护），也可以放在数据目录的 `quests/` 与 `presets/` 只读 YAML 里。

> 基于 YLib（必要时可直接改 YLib 源码；注意它的 `api` / `core` 是 **Java 8**，本项目是 Java 21）。箱子菜单框架（`cn.yvmou.ylib.gui`：布局即文本图、静态/动态槽位）也在 YLib 里，是给所有用 YLib 的项目共用的界面设施——改它要按库来要求自己。
> 设计与模块划分见 [`ARCHITECTURE.md`](ARCHITECTURE.md)，逐类职责见 [`CODE_MAP.md`](CODE_MAP.md)，面向使用者的文档在 `docs/`——**改完代码要同步更新这三处**。

- `.\gradlew.bat clean build` 完整构建　`.\gradlew.bat :core:compileJava` 只编译后端（几秒）　`.\gradlew.bat :core:test` 跑测试
- `.\start-folia.ps1` 构建 → 复制产物到 `run/plugins` → 启动测试服（`-Clean` / `-SkipBuild` / `-Foreground`）；该脚本必须保留 **UTF-8 BOM**，否则 PowerShell 5.1 按 ANSI 读中文会报解析错。
- 全程用**中文**交流，代码注释也用中文并写「为什么」；**注释能一行说清就一行**，类注释尤其如此，别写七八行的长篇论证（那些属于 `ARCHITECTURE.md`）。
- **简洁优先**，能用一处改动解决的不要包一层适配器；**不考虑向后兼容**，死代码直接删；危险或不可逆的操作先问。
- 行为尽量**用测试钉住**，尤其是「不报错的错误」（字段类型写错、元数据与说明矛盾、依赖字节码版本这类编译期抓不到的问题）。无法自行验证的部分（真人进服、actionbar/title 实际显示）如实说明「未验证」。
