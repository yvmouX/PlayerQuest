package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.YLib;
import cn.yvmou.ylib.YLibException;
import cn.yvmou.ylib.logger.Logger;
import cn.yvmou.ylib.message.MessageService;
import cn.yvmou.ylib.message.MessageSettings;
import cn.yvmou.ylib.scheduler.UniversalScheduler;
import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import com.playerPlugin.playerTaskX.core.command.AdminCommand;
import com.playerPlugin.playerTaskX.core.command.PlayerCommand;
import com.playerPlugin.playerTaskX.core.period.PeriodicService;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import cn.yvmou.ylib.gui.MenuListener;
import com.playerPlugin.playerTaskX.core.gui.editor.ChatInputListener;
import com.playerPlugin.playerTaskX.core.integration.customcontent.CustomContentHooks;
import com.playerPlugin.playerTaskX.core.integration.customfishing.CustomFishingHook;
import com.playerPlugin.playerTaskX.core.integration.mythicmobs.MythicMobsHook;
import com.playerPlugin.playerTaskX.core.listener.BlockListener;
import com.playerPlugin.playerTaskX.core.listener.EntityListener;
import com.playerPlugin.playerTaskX.core.listener.ItemListener;
import com.playerPlugin.playerTaskX.core.listener.PlayerListener;
import com.playerPlugin.playerTaskX.core.listener.TextListener;
import com.playerPlugin.playerTaskX.core.display.ProgressDisplay;
import com.playerPlugin.playerTaskX.core.placeholder.PlaceholderHook;
import com.playerPlugin.playerTaskX.core.quest.QuestAdminService;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.RewardRegistryImpl;
import com.playerPlugin.playerTaskX.core.engine.RewardService;
import com.playerPlugin.playerTaskX.core.storage.DatabaseFactory;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.PresetRepository;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;
import com.playerPlugin.playerTaskX.core.storage.yaml.DefinitionFolder;
import com.playerPlugin.playerTaskX.core.storage.yaml.ExampleDefinitions;
import com.playerPlugin.playerTaskX.core.storage.yaml.MergedPresetRepository;
import com.playerPlugin.playerTaskX.core.storage.yaml.MergedQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.yaml.YamlDefinitions;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import com.playerPlugin.playerTaskX.core.objective.ObjectiveBuiltIns;
import com.playerPlugin.playerTaskX.core.reward.RewardBuiltIns;


public final class PlayerTaskX extends JavaPlugin {

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
    private QuestRegistryImpl quests;
    private ObjectiveRegistryImpl objectiveTypes;
    private RewardRegistryImpl rewardTypes;
    private ProgressService progressService;
    private RewardService rewardService;
    private ProgressDisplay progressDisplay;
    private PeriodicService periodicService;

    /** 自定义内容来源（ItemsAdder / CraftEngine）：没装时是空实现，监听器与目录都会拿到空表。 */
    private CustomContentHooks customContent = CustomContentHooks.empty();
    private QuestAdminService questAdmin;
    /** MythicMobs 接入点；null 表示未安装或不支持（原版击杀照常工作）。 */
    private MythicMobsHook mythicMobs;

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
        // 仓储都只在启用阶段用一次，故留作局部变量，不占实例字段（presets 例外：预设引用要按它展开）
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
        rewardService = new RewardService(quests, rewardTypes, playerQuestRepository);
        progressDisplay = new ProgressDisplay(config, quests, playerQuestRepository, messages, objectiveTypes);
        periodicService = new PeriodicService(config, quests, playerQuestRepository, progressService);
        questAdmin = new QuestAdminService(questDefinitions, quests, objectiveTypes, rewardService,
                progressService,
                // 在线玩家列表延迟到使用时才取：保存/删除发生在运行期，装配时还没有玩家
                () -> getServer().getOnlinePlayers().stream().map(Player::getUniqueId).toList(),
                // 预设仓储同样是运行期查询：任务里的 preset: 引用按它展开（见 PresetRefs）
                id -> presets.findById(id).orElse(null),
                // 自定义内容接入点在下面几步才装配完，因此传的是「用的时候再取」的 supplier
                this::customContent);

        // ---------- 任务数据 ----------
        guard("任务载入", questAdmin::reload);

        // ---------- 软依赖接入（游戏内容插件） ----------
        // 必须在任务载入之后：这两个钩子不改任务数据，但要赶在监听器注册之前就位
        guard("MythicMobs 接入", () -> mythicMobs = MythicMobsHook.create());
        guard("自定义内容接入（ItemsAdder / CraftEngine）", () -> customContent = CustomContentHooks.create());

        // ---------- 事件、命令与调度 ----------
        // 每个子系统独立守护：任何一项失败都应只损失该功能，
        // 而不是让整个插件（乃至服务端启动）失败
        guard("事件监听器", this::registerListeners);
        guard("命令注册", this::registerCommands);
        guard("进度展示调度", () -> progressDisplay.startAutoRefresh(ylib.getScheduler()));
        guard("每日任务调度", () ->
                periodicService.startResetCheck(ylib.getScheduler(), messages, () -> getServer().getOnlinePlayers()));
        guard("PlaceholderAPI 变量", () -> PlaceholderHook.register(this));

        log.info("PlayerTaskX 已启用（{} 个任务，{} 种目标，{} 种奖励）",
                quests.all().size(), objectiveTypes.all().size(), rewardTypes.all().size());
        // 命令清单不便在控制台逐条刷屏，这里给出入口——否则用户很难发现有哪些命令
        log.info("命令：玩家 /ptx（别名 /playertaskx），管理员 /ptxa（别名 /playertaskxadmin）；"
                + "用 /ptx help 与 /ptxa help 查看子命令清单");
    }

    /** 在数据库之外接上 {@code quests/} 与 {@code presets/} 两个只读 YAML 目录；库优先等合并规则由 {@link MergedQuestRepository} / {@link MergedPresetRepository} 承担。 */
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
     * 目录空着时铺一份示例定义（示例随插件发布，见 {@link ExampleDefinitions}）。
     * <p>
     * 只在目录完全为空时动手：管理员删掉某几个示例、或放了自己的定义之后，
     * 重启时不该把它们变回来。
     */
    private void seedExampleFiles(DefinitionFolder questFolder, DefinitionFolder presetFolder) {
        int quests = ExampleDefinitions.seedQuests(questFolder);
        int presetsWritten = ExampleDefinitions.seedPresets(presetFolder);
        if (quests > 0) {
            log.info("quests/ 是空的，已铺入 {} 个示例任务文件（只读来源，可自由删改）", quests);
        }
        if (presetsWritten > 0) {
            log.info("presets/ 是空的，已铺入 {} 个示例预设文件（只读来源，可自由删改）", presetsWritten);
        }
    }

    /** 执行一个启动步骤并隔离异常：失败只记日志（写明是哪一步），启动期的环境问题不该让玩家连主功能都用不了。 */
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

    /** 登记内置目标类型；清单唯一来源是 {@link ObjectiveBuiltIns}，新增类型在那里显式登记。 */
    private void registerBuiltInObjectives() {
        ObjectiveBuiltIns.all().forEach(objectiveTypes::register);
        var rejected = objectiveTypes.rejected();
        if (!rejected.isEmpty()) {
            log.warn("有目标类型注册失败: {}", rejected);
        }
    }

    /** 登记内置奖励类型。 */
    private void registerBuiltInRewards() {
        RewardBuiltIns.all().forEach(rewardTypes::register);
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
                new BlockListener(progressService, progressDisplay::onProgressApplied, customContent), this);
        getServer().getPluginManager().registerEvents(
                new EntityListener(progressService, progressDisplay::onProgressApplied, mythicMobs), this);
        getServer().getPluginManager().registerEvents(
                new ItemListener(progressService, progressDisplay::onProgressApplied, customContent), this);
        getServer().getPluginManager().registerEvents(
                new TextListener(progressService, progressDisplay::onProgressApplied), this);
        getServer().getPluginManager().registerEvents(
                new PlayerListener(progressService, progressDisplay, periodicService, messages), this);
        // CustomFishing 的钓获事件只存在于它的 API 里，因此监听器由钩子反射创建后再注册
        CustomFishingHook.register(this, progressService, progressDisplay::onProgressApplied);
        // 菜单点击分发：没有它玩家能打开界面但点击无反应
        MenuListener.init(this);
        // 编辑器的聊天栏输入：没有它管理员点完字段就没了下文
        getServer().getPluginManager().registerEvents(new ChatInputListener(), this);
    }

    // ---------- 供命令 / GUI 使用的访问点 ----------

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

    /** 自定义内容来源（ItemsAdder / CraftEngine）；SPI 查询为空实现。 */
    public CustomContentHooks customContent() {
        return customContent;
    }

    public PeriodicService periodicService() {
        return periodicService;
    }

    /** 任务定义维护入口（保存/删除/重载/校验），管理命令与管理 GUI 共用。 */
    public QuestAdminService questAdmin() {
        return questAdmin;
    }


    /**
     * 任务定义仓储（数据库 + 可选的 YAML 只读来源）。
     * <p>
     * 命令与 GUI 用它问 {@code isReadOnly(id)}——文件里的定义不可写；
     * 写操作仍然走 {@link #questAdmin()}。
     */
    public QuestRepository questDefinitions() {
        return questDefinitions;
    }

    public PlayerQuestRepository playerQuestRepository() {
        return playerQuestRepository;
    }

    /** 跨平台调度器（Folia 的实体/区域调度与 Spigot 的 Bukkit 调度都由它统一）。 */
    public UniversalScheduler scheduler() {
        return ylib.getScheduler();
    }

    /** 目标/奖励预设仓储（只读浏览用；写入仍走 QuestAdminService）。 */
    public PresetRepository presetDefinitions() {
        return presets;
    }
}

