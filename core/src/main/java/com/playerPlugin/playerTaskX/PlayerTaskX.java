package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.YLib;
import cn.yvmou.ylib.YLibException;
import cn.yvmou.ylib.logger.Logger;
import cn.yvmou.ylib.message.MessageService;
import cn.yvmou.ylib.message.MessageSettings;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import com.playerPlugin.playerTaskX.core.command.AdminCommand;
import com.playerPlugin.playerTaskX.core.command.PlayerCommand;
import com.playerPlugin.playerTaskX.core.period.PeriodicService;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.gui.MenuListener;
import com.playerPlugin.playerTaskX.core.integration.CustomFishingHook;
import com.playerPlugin.playerTaskX.core.integration.MythicMobsHook;
import com.playerPlugin.playerTaskX.core.listener.BlockListener;
import com.playerPlugin.playerTaskX.core.listener.EntityListener;
import com.playerPlugin.playerTaskX.core.listener.ItemListener;
import com.playerPlugin.playerTaskX.core.listener.PlayerListener;
import com.playerPlugin.playerTaskX.core.listener.TextListener;
import com.playerPlugin.playerTaskX.core.progress.ProgressDisplay;
import com.playerPlugin.playerTaskX.core.placeholder.PlaceholderHook;
import com.playerPlugin.playerTaskX.core.quest.PrerequisiteService;
import com.playerPlugin.playerTaskX.core.quest.QuestAdminService;
import com.playerPlugin.playerTaskX.core.registry.BuiltIns;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.RewardRegistryImpl;
import com.playerPlugin.playerTaskX.core.reward.RewardService;
import com.playerPlugin.playerTaskX.core.seed.ExampleFiles;
import com.playerPlugin.playerTaskX.core.seed.ExamplePresets;
import com.playerPlugin.playerTaskX.core.seed.ExampleQuests;
import com.playerPlugin.playerTaskX.core.storage.DatabaseFactory;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.PresetRepository;
import com.playerPlugin.playerTaskX.core.storage.QuestClaimRepository;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;
import com.playerPlugin.playerTaskX.core.storage.yaml.DefinitionFolder;
import com.playerPlugin.playerTaskX.core.storage.yaml.MergedPresetRepository;
import com.playerPlugin.playerTaskX.core.storage.yaml.MergedQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.yaml.YamlDefinitions;
import com.playerPlugin.playerTaskX.core.web.EditorServer;
import com.playerPlugin.playerTaskX.core.web.EditorServices;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;


/**
 * 插件入口，只做三件事：
 * <ol>
 *   <li><b>装配</b>：按依赖顺序构造各子系统（看 onEnable 的构造顺序即依赖图）；</li>
 *   <li><b>启停</b>：按序开启/关闭各子系统，单步失败互不拖垮（{@link #guard}）；</li>
 *   <li><b>访问点</b>：向命令、GUI、编辑器暴露各子系统。</li>
 * </ol>
 * 业务逻辑不住在这里：周期任务逻辑在 PeriodicService，任务维护在 QuestAdminService，
 * 事件翻译在 listener 包，类型清单在 BuiltIns。往本类加方法前先想想它属于哪个子系统。
 *
 * <h2>为什么 implements {@link EditorServices}</h2>
 * 网页编辑器后台只认那个窄接口（这样它能脱离服务端单测），而它需要的子系统恰好都由本类持有，
 * 因此由本类直接实现：转调一行，比再包一层适配器少一处要同步维护的地方。
 */
public final class PlayerTaskX extends JavaPlugin implements EditorServices {

    private static PlayerTaskX instance;
    private static Logger log;

    private YLib ylib;
    private MessageService messages;
    private PluginConfig config;
    private DatabaseFactory.Handle database;
    private PresetRepository presets;
    /** 任务定义仓储：数据库 + 可选的 quests/ 只读 YAML（库优先）。 */
    private QuestRepository questDefinitions;
    private PlayerQuestRepository playerQuestRepository;
    private QuestClaimRepository claimRepository;
    private QuestRegistryImpl quests;
    private ObjectiveRegistryImpl objectiveTypes;
    private RewardRegistryImpl rewardTypes;
    private PrerequisiteService prerequisites;
    private ProgressService progressService;
    private RewardService rewardService;
    private ProgressDisplay progressDisplay;
    private PeriodicService periodicService;
    private QuestAdminService questAdmin;
    /** MythicMobs 接入点；null 表示未安装或不支持（原版击杀照常工作）。 */
    private MythicMobsHook mythicMobs;
    private EditorServer editorServer;

    public static PlayerTaskX getInstance() {
        return instance;
    }

    public static Logger log() {
        return log;
    }

    @Override
    public void onEnable() {
        instance = this;

        // ---------- 基础设施（YLib） ----------
        try {
            ylib = YLib.init(this);
        } catch (YLibException e) {
            getLogger().severe("YLib 初始化失败: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        log = ylib.getLogger();

        try {
            config = ylib.getConfigurationManager().registerConfiguration(PluginConfig.class);
        } catch (RuntimeException e) {
            log.error("配置加载失败，插件将被禁用: {}", e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // YLib 的消息服务自带文本渲染（MiniMessage 为主，兼容 & 与 § 颜色码），
        // 出口已是渲染好的 § 形式，这里不需要再包一层
        messages = ylib.createMessageService(MessageSettings.builder()
                .defaultLanguage(config.getLanguageDefault())
                .availableLanguages(config.getLanguageAvailable().toArray(new String[0]))
                // 默认文件模式是 lang_%s.yml，这里改用更贴近惯例的 <code>.yml
                .filePattern("%s.yml")
                .languageFolder("lang")
                .useClientLocale(config.isLanguageUseClientLocale())
                .build());

        // ---------- 存储 ----------
        // 任务定义、预设与玩家数据共用一个库，因此只有这一次装配；
        // 仓储都只在启用阶段用一次，故留作局部变量，不占实例字段（presets 例外，编辑器要用）
        DatabaseFactory.Handle handle;
        try {
            handle = DatabaseFactory.open(config, getDataFolder());
            database = handle;
        } catch (RuntimeException e) {
            log.error("数据库打开失败，插件将被禁用: {}", e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        playerQuestRepository = handle.playerQuestRepository();
        claimRepository = handle.claims();
        presets = handle.presets();
        questDefinitions = handle.quests();
        // 定义来源：数据库（权威、可写）+ 可选的 quests/ 与 presets/ 只读 YAML（库优先）
        if (config.isDefinitionsReadFiles()) {
            questDefinitions = withYamlDefinitions(handle);
        }
        log.info("存储已就绪: {}（任务定义、预设与玩家数据在同一库）", database.description());

        // ---------- 注册表 ----------
        quests = new QuestRegistryImpl();
        objectiveTypes = new ObjectiveRegistryImpl();
        rewardTypes = new RewardRegistryImpl();
        registerBuiltInObjectives();
        registerBuiltInRewards();

        // ---------- 引擎 ----------
        progressService = new ProgressService(quests, objectiveTypes, playerQuestRepository);
        // 目标结构变化导致进度重置时明确告知——静默错配比进度归零更难排查
        progressService.onStructureChanged((questId, playerId) -> log.warn(
                "任务 {} 的目标结构已变化，玩家 {} 的该任务进度已重置（旧进度的下标已错位）",
                questId, playerId));
        // 前置判定只依赖「永久领取账本」：每日任务记录会被整批删除，不能拿它当依据
        prerequisites = new PrerequisiteService(quests, claimRepository);
        rewardService = new RewardService(quests, rewardTypes, playerQuestRepository,
                claimRepository, prerequisites);
        progressDisplay = new ProgressDisplay(config, quests, playerQuestRepository, messages, objectiveTypes);
        periodicService = new PeriodicService(config, quests, playerQuestRepository, progressService, prerequisites);
        questAdmin = new QuestAdminService(questDefinitions, quests, objectiveTypes, rewardService,
                progressService, prerequisites,
                // 在线玩家列表延迟到使用时才取：保存/删除发生在运行期，装配时还没有玩家
                () -> getServer().getOnlinePlayers().stream().map(Player::getUniqueId).toList(),
                // 预设仓储同样是运行期查询：任务里的 preset: 引用按它展开（见 PresetRefs）
                id -> presets.findById(id).orElse(null));

        // ---------- 任务数据 ----------
        // 读库/写示例任务都可能因磁盘或连接问题失败，单独守护，
        // 让插件以「零任务」状态启动而不是直接崩掉
        guard("示例任务写入", () -> questAdmin.seedIfEmpty(ExampleQuests.all(config.periodic(QuestType.DAILY).refreshCost())));
        guard("默认预设写入", () -> presets.seedIfEmpty(ExamplePresets.all()));
        guard("任务载入", questAdmin::reload);

        // ---------- 软依赖接入（游戏内容插件） ----------
        // 必须在任务载入之后：这两个钩子不改任务数据，但要赶在监听器注册之前就位
        guard("MythicMobs 接入", () -> mythicMobs = MythicMobsHook.create());

        // ---------- 事件、命令与调度 ----------
        // 每个子系统独立守护：任何一项失败都应只损失该功能，
        // 而不是让整个插件（乃至服务端启动）失败
        guard("事件监听器", this::registerListeners);
        guard("命令注册", this::registerCommands);
        guard("进度展示调度", () -> progressDisplay.startAutoRefresh(ylib.getScheduler()));
        guard("每日任务调度", () ->
                periodicService.startResetCheck(ylib.getScheduler(), messages, () -> getServer().getOnlinePlayers()));
        guard("网页编辑器", this::startEditor);
        guard("PlaceholderAPI 变量", () -> PlaceholderHook.register(this));

        log.info("PlayerTaskX 已启用（{} 个任务，{} 种目标，{} 种奖励）",
                quests.all().size(), objectiveTypes.all().size(), rewardTypes.all().size());
        // 命令清单不便在控制台逐条刷屏，这里给出入口——否则用户很难发现有哪些命令
        log.info("命令：玩家 /ptx（别名 /playertaskx），管理员 /ptxa（别名 /playertaskxadmin）；"
                + "用 /ptx help 与 /ptxa help 查看子命令清单");
    }

    /**
     * 在数据库之外接上 {@code quests/} 与 {@code presets/} 两个只读 YAML 目录。
     *
     * <p>与数据库的合并规则（库优先、冲突告警、文件定义只读）由
     * {@link MergedQuestRepository} / {@link MergedPresetRepository} 承担，这里只做装配：
     * 建目录、铺一份示例、把告警接到插件日志。
     */
    private QuestRepository withYamlDefinitions(DatabaseFactory.Handle handle) {
        java.util.function.Consumer<String> warner = message -> log.warn("YAML 定义: {}", message);
        DefinitionFolder questFolder = DefinitionFolder.of(getDataFolder(), "quests", warner);
        DefinitionFolder presetFolder = DefinitionFolder.of(getDataFolder(), "presets", warner);
        questFolder.ensureExists();
        presetFolder.ensureExists();
        seedExampleFiles(questFolder, presetFolder);
        presets = new MergedPresetRepository(handle.presets(), YamlDefinitions.presetSources(presetFolder), warner);
        return new MergedQuestRepository(handle.quests(), YamlDefinitions.questSources(questFolder), warner);
    }

    /**
     * 目录空着时铺一份示例定义（见 {@link ExampleFiles}）。
     * <p>
     * 只在目录完全为空时动手：管理员删掉某几个示例、或放了自己的定义之后，
     * 重启时不该把它们变回来。
     */
    private void seedExampleFiles(DefinitionFolder questFolder, DefinitionFolder presetFolder) {
        int quests = ExampleFiles.writeQuests(questFolder, ExampleQuests.all(config.periodic(QuestType.DAILY).refreshCost()));
        int presetsWritten = ExampleFiles.writePresets(presetFolder, ExamplePresets.all());
        if (quests > 0) {
            log.info("quests/ 是空的，已写入 {} 个示例任务文件（只读来源，可自由删改）", quests);
        }
        if (presetsWritten > 0) {
            log.info("presets/ 是空的，已写入 {} 个示例预设文件（只读来源，可自由删改）", presetsWritten);
        }
    }

    /**
     * 执行一个启动步骤并隔离其异常。
     * <p>
     * 启动期的失败往往来自环境（端口占用、软依赖缺失、类加载冲突），
     * 这些都不该让玩家连插件的主功能都用不了。失败时明确记录是哪一步，
     * 而不是留下一段难以定位的堆栈。
     */
    private void guard(String step, Runnable action) {
        try {
            action.run();
        } catch (Throwable e) {
            log.error("初始化「{}」失败，该功能将不可用: {}", step, e.toString());
            getLogger().log(java.util.logging.Level.WARNING, "PlayerTaskX 初始化步骤失败: " + step, e);
        }
    }

    @Override
    public void onDisable() {
        // 先停运行期任务再关底层资源：定时器若在库关闭后触发会报连接错误
        periodicService.shutdown();
        progressDisplay.shutdown();
        if (editorServer != null) {
            editorServer.stop();
            editorServer = null;
        }
        HandlerList.unregisterAll(this);
        if (database != null) {
            database.close();
            database = null;
        }
        instance = null;
        if (log != null) {
            log.info("PlayerTaskX 已禁用");
        }
    }

    /** 登记内置目标类型；清单唯一来源是 {@link BuiltIns}，新增类型在那里显式登记。 */
    private void registerBuiltInObjectives() {
        BuiltIns.objectives().forEach(objectiveTypes::register);
        var rejected = objectiveTypes.rejected();
        if (!rejected.isEmpty()) {
            log.warn("有目标类型注册失败: {}", rejected);
        }
    }

    /** 登记内置奖励类型。 */
    private void registerBuiltInRewards() {
        BuiltIns.rewards().forEach(rewardTypes::register);
        // 软依赖缺失时明确告知管理员，否则玩家做完任务拿不到奖励却查不出原因
        for (var type : rewardTypes.all()) {
            if (!type.available()) {
                log.warn("奖励类型 {} 当前不可用: {}", type.id(), type.unavailableReason());
            }
        }
    }

    private void registerCommands() {
        ylib.getCommandManager().register(new PlayerCommand());
        ylib.getCommandManager().register(new AdminCommand());
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new BlockListener(progressService, progressDisplay::onProgressApplied), this);
        getServer().getPluginManager().registerEvents(
                new EntityListener(progressService, progressDisplay::onProgressApplied, mythicMobs), this);
        getServer().getPluginManager().registerEvents(
                new ItemListener(progressService, progressDisplay::onProgressApplied), this);
        getServer().getPluginManager().registerEvents(
                new TextListener(progressService, progressDisplay::onProgressApplied), this);
        getServer().getPluginManager().registerEvents(
                new PlayerListener(progressService, progressDisplay, periodicService, messages), this);
        // CustomFishing 的钓获事件只存在于它的 API 里，因此监听器由钩子反射创建后再注册
        CustomFishingHook.register(this, progressService, progressDisplay::onProgressApplied);
        // 菜单点击分发：没有它玩家能打开界面但点击无反应
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
    }

    /**
     * 启动内置网页编辑器。
     * <p>
     * 首选端口被占用时由 {@link EditorServer} 自动 +1 并记 warn，
     * {@code /ptxa editor} 始终报告实际端口。失败只记录日志，不影响插件其它功能。
     */
    private void startEditor() {
        // 译名准备与「编辑器是否启用」无关：中文语言文件是磁盘上的缓存，
        // 这次没开编辑器时先取好，下次打开就能直接用。
        editorServer = new EditorServer(this);
        editorServer.prepareCatalog();
        if (!config.isEditorEnabled()) {
            return;
        }
        editorServer.start(config.getEditorPort());
    }

    // ---------- 供命令 / GUI / 编辑器使用的访问点 ----------

    public MessageService messages() {
        return messages;
    }

    public PluginConfig config() {
        return config;
    }

    public QuestRegistryImpl quests() {
        return quests;
    }

    public ObjectiveRegistryImpl objectiveTypes() {
        return objectiveTypes;
    }

    public RewardRegistryImpl rewardTypes() {
        return rewardTypes;
    }

    public ProgressService progressService() {
        return progressService;
    }

    /** 前置任务判定与校验（任务链）。 */
    public PrerequisiteService prerequisites() {
        return prerequisites;
    }

    public RewardService rewardService() {
        return rewardService;
    }

    public ProgressDisplay progressDisplay() {
        return progressDisplay;
    }

    public PeriodicService periodicService() {
        return periodicService;
    }

    /** 任务定义维护入口（保存/删除/重载/校验），管理命令、管理 GUI 与编辑器后台共用。 */
    public QuestAdminService questAdmin() {
        return questAdmin;
    }

    /** 目标/奖励预设仓储；与任务定义、玩家数据同一个库。 */
    public PresetRepository presets() {
        return presets;
    }

    /**
     * 任务定义仓储（数据库 + 可选的 YAML 只读来源）。
     * <p>
     * 编辑器用它问 {@code isReadOnly(id)}——文件里的定义在界面上是只读的；
     * 写操作仍然走 {@link #questAdmin()}。
     */
    @Override
    public QuestRepository questDefinitions() {
        return questDefinitions;
    }

    public PlayerQuestRepository playerQuestRepository() {
        return playerQuestRepository;
    }

    /** 永久领取账本：前置判定与「做过没有」查询的唯一依据。 */
    public QuestClaimRepository claimRepository() {
        return claimRepository;
    }

    /**
     * MythicMobs 接入点；未安装或不支持时为 {@code null}。
     * <p>
     * 编辑器用它把自定义怪物列进实体选择器（{@code mythic:<怪物id>}）。
     */
    public MythicMobsHook mythicMobs() {
        return mythicMobs;
    }

    /** 存储描述，供编辑器与命令展示。 */
    public String describeStorage() {
        return database == null ? "未连接" : database.description();
    }

    // ---------- EditorServices：只有编辑器会用到的那几个值 ----------
    // 它们不是「子系统访问点」，而是把子系统里的具体取值抽出来，免得编辑器直接摸 config / jar 资源

    /** 网页编辑器实例；未启用或启动失败时为 null。 */
    public EditorServer editorServer() {
        return editorServer;
    }
}
