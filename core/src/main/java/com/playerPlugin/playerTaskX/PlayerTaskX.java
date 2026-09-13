package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.YLib;
import cn.yvmou.ylib.YLibException;
import cn.yvmou.ylib.logger.Logger;
import cn.yvmou.ylib.message.MessageService;
import cn.yvmou.ylib.message.MessageSettings;
import cn.yvmou.ylib.text.TextRenderer;
import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import com.playerPlugin.playerTaskX.core.command.AdminCommand;
import com.playerPlugin.playerTaskX.core.command.PlayerCommand;
import com.playerPlugin.playerTaskX.core.daily.DailyService;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.gui.MenuListener;
import com.playerPlugin.playerTaskX.core.listener.BlockListener;
import com.playerPlugin.playerTaskX.core.listener.EntityListener;
import com.playerPlugin.playerTaskX.core.listener.ItemListener;
import com.playerPlugin.playerTaskX.core.listener.PlayerListener;
import com.playerPlugin.playerTaskX.core.listener.TextListener;
import com.playerPlugin.playerTaskX.core.progress.ProgressDisplay;
import com.playerPlugin.playerTaskX.core.placeholder.PlaceholderHook;
import com.playerPlugin.playerTaskX.core.quest.QuestAdminService;
import com.playerPlugin.playerTaskX.core.registry.BuiltIns;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.RewardRegistryImpl;
import com.playerPlugin.playerTaskX.core.reward.RewardService;
import com.playerPlugin.playerTaskX.core.seed.ExampleQuests;
import com.playerPlugin.playerTaskX.core.storage.DatabaseFactory;
import com.playerPlugin.playerTaskX.core.storage.DefinitionMigrator;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.PresetRepository;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;
import com.playerPlugin.playerTaskX.core.storage.StorageFactory;
import com.playerPlugin.playerTaskX.core.web.EditorServer;
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
 * 业务逻辑不住在这里：每日逻辑在 DailyService，任务维护在 QuestAdminService，
 * 事件翻译在 listener 包，类型清单在 BuiltIns。往本类加方法前先想想它属于哪个子系统。
 */
public final class PlayerTaskX extends JavaPlugin {

    private static PlayerTaskX instance;
    private static Logger log;

    private YLib ylib;
    private MessageService messages;
    private PluginConfig config;
    private DatabaseFactory.Handle database;
    private StorageFactory storage;
    private QuestRepository questRepository;
    private PresetRepository presets;
    private PlayerQuestRepository playerQuestRepository;
    private QuestRegistryImpl quests;
    private ObjectiveRegistryImpl objectiveTypes;
    private RewardRegistryImpl rewardTypes;
    private ProgressService progressService;
    private RewardService rewardService;
    private ProgressDisplay progressDisplay;
    private DailyService dailyService;
    private QuestAdminService questAdmin;
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
        log.info("玩家数据存储已就绪: {}", database.description());

        // 任务定义与预设：按 definitions.type 选择后端（默认 JSON 文件）。
        // 迁移必须在装配之前完成——否则新后端读到的是空的，随后会把旧定义当成"不存在"。
        DefinitionMigrator.migrateIfNeeded(config, getDataFolder(), handle, log::info, log::warn);
        try {
            storage = StorageFactory.create(config, getDataFolder(), handle.database(),
                    log::warn, log::info);
            questRepository = storage.quests();
            presets = storage.presets();
            log.info("任务定义与预设存储已就绪: {}", storage.description());
        } catch (RuntimeException e) {
            log.error("任务定义存储初始化失败，插件将被禁用: {}", e.getMessage());
            database.close();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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
        rewardService = new RewardService(quests, rewardTypes, playerQuestRepository);
        progressDisplay = new ProgressDisplay(config, quests, playerQuestRepository, messages, objectiveTypes);
        dailyService = new DailyService(config, quests, playerQuestRepository, progressService);
        questAdmin = new QuestAdminService(questRepository, quests, objectiveTypes, rewardService,
                progressService,
                // 在线玩家列表延迟到使用时才取：保存/删除发生在运行期，装配时还没有玩家
                () -> getServer().getOnlinePlayers().stream().map(Player::getUniqueId).toList());

        // ---------- 任务数据 ----------
        // 读库/写示例任务都可能因磁盘或连接问题失败，单独守护，
        // 让插件以「零任务」状态启动而不是直接崩掉
        guard("示例任务写入", () -> questAdmin.seedIfEmpty(ExampleQuests.all(config.getDailyRefreshCost())));
        guard("任务载入", questAdmin::reload);

        // ---------- 事件、命令与调度 ----------
        // 每个子系统独立守护：任何一项失败都应只损失该功能，
        // 而不是让整个插件（乃至服务端启动）失败
        guard("事件监听器", this::registerListeners);
        guard("命令注册", this::registerCommands);
        guard("进度展示调度", () -> progressDisplay.startAutoRefresh(ylib.getScheduler()));
        guard("每日任务调度", () ->
                dailyService.startResetCheck(ylib.getScheduler(), messages, () -> getServer().getOnlinePlayers()));
        guard("网页编辑器", this::startEditor);
        guard("PlaceholderAPI 变量", () -> PlaceholderHook.register(this));

        log.info("PlayerTaskX 已启用（{} 个任务，{} 种目标，{} 种奖励）",
                quests.all().size(), objectiveTypes.all().size(), rewardTypes.all().size());
        // 命令清单不便在控制台逐条刷屏，这里给出入口——否则用户很难发现有哪些命令
        log.info("命令：玩家 /ptx（别名 /playertaskx），管理员 /ptxa（别名 /playertaskxadmin）；"
                + "用 /ptx help 与 /ptxa help 查看子命令清单");
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
        dailyService.shutdown();
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
        getServer().getPluginManager().registerEvents(new BlockListener(progressService, progressDisplay::onProgressApplied), this);
        getServer().getPluginManager().registerEvents(new EntityListener(progressService, progressDisplay::onProgressApplied), this);
        getServer().getPluginManager().registerEvents(new ItemListener(progressService, progressDisplay::onProgressApplied), this);
        getServer().getPluginManager().registerEvents(new TextListener(progressService, progressDisplay::onProgressApplied), this);
        getServer().getPluginManager().registerEvents(
                new PlayerListener(progressService, progressDisplay, dailyService, messages), this);
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

    public RewardService rewardService() {
        return rewardService;
    }

    public ProgressDisplay progressDisplay() {
        return progressDisplay;
    }

    public DailyService dailyService() {
        return dailyService;
    }

    /** 任务定义维护入口（保存/删除/重载/校验），管理命令、管理 GUI 与编辑器后台共用。 */
    public QuestAdminService questAdmin() {
        return questAdmin;
    }

    public QuestRepository questRepository() {
        return questRepository;
    }

    /** 目标/奖励预设仓储；后端与任务定义一致（见 {@code definitions.type}）。 */
    public PresetRepository presets() {
        return presets;
    }

    public PlayerQuestRepository playerQuestRepository() {
        return playerQuestRepository;
    }

    public YLib ylib() {
        return ylib;
    }

    /** 渲染文本（MiniMessage 优先，兼容 & / § 颜色码）。 */
    public String text(String raw) {
        return TextRenderer.render(raw);
    }

    /** 存储描述，供编辑器与命令展示。 */
    public String describeStorage() {
        return database == null ? "未连接" : database.description();
    }

    /** 网页编辑器实例；未启用或启动失败时为 null。 */
    public EditorServer editorServer() {
        return editorServer;
    }
}
