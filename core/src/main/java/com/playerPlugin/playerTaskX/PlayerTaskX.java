package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.YLib;
import cn.yvmou.ylib.YLibException;
import cn.yvmou.ylib.logger.Logger;
import cn.yvmou.ylib.message.MessageService;
import cn.yvmou.ylib.message.MessageSettings;
import cn.yvmou.ylib.scheduler.UniversalScheduler;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import com.playerPlugin.playerTaskX.core.command.AdminCommand;
import com.playerPlugin.playerTaskX.core.command.PlayerCommand;
import com.playerPlugin.playerTaskX.core.daily.DailyService;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.gui.MenuListener;
import com.playerPlugin.playerTaskX.core.reward.CurrencyType;
import com.playerPlugin.playerTaskX.core.reward.MoneyReward;
import com.playerPlugin.playerTaskX.core.listener.BlockListener;
import com.playerPlugin.playerTaskX.core.listener.EntityListener;
import com.playerPlugin.playerTaskX.core.listener.ItemListener;
import com.playerPlugin.playerTaskX.core.listener.PlayerListener;
import com.playerPlugin.playerTaskX.core.objective.BreedObjective;
import com.playerPlugin.playerTaskX.core.objective.BreakBlockObjective;
import com.playerPlugin.playerTaskX.core.objective.ChatObjective;
import com.playerPlugin.playerTaskX.core.objective.CommandObjective;
import com.playerPlugin.playerTaskX.core.objective.ConsumeObjective;
import com.playerPlugin.playerTaskX.core.objective.CraftObjective;
import com.playerPlugin.playerTaskX.core.objective.EnchantObjective;
import com.playerPlugin.playerTaskX.core.objective.FishObjective;
import com.playerPlugin.playerTaskX.core.objective.InteractObjective;
import com.playerPlugin.playerTaskX.core.objective.KillObjective;
import com.playerPlugin.playerTaskX.core.objective.PlaceBlockObjective;
import com.playerPlugin.playerTaskX.core.objective.ShearObjective;
import com.playerPlugin.playerTaskX.core.objective.SubmitObjective;
import com.playerPlugin.playerTaskX.core.objective.TameObjective;
import com.playerPlugin.playerTaskX.core.progress.ProgressDisplay;
import com.playerPlugin.playerTaskX.core.quest.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.RewardRegistryImpl;
import com.playerPlugin.playerTaskX.core.reward.CommandReward;
import com.playerPlugin.playerTaskX.core.reward.ExpReward;
import com.playerPlugin.playerTaskX.core.reward.ItemReward;
import com.playerPlugin.playerTaskX.core.reward.MoneyReward;
import com.playerPlugin.playerTaskX.core.reward.PointsReward;
import com.playerPlugin.playerTaskX.core.reward.RewardService;
import com.playerPlugin.playerTaskX.core.storage.DatabaseFactory;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;
import com.playerPlugin.playerTaskX.core.text.LangMessageService;
import com.playerPlugin.playerTaskX.core.text.TextRenderer;
import com.playerPlugin.playerTaskX.core.web.EditorServer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 插件入口：只负责装配依赖，不含业务逻辑。
 * <p>
 * 装配顺序即依赖顺序：配置 → 存储 → 注册表 → 引擎 → 监听器 → 展示。
 */
public final class PlayerTaskX extends JavaPlugin {

    private static PlayerTaskX instance;
    private static Logger log;

    private YLib ylib;
    private MessageService messages;
    private PluginConfig config;
    private DatabaseFactory.Handle database;
    private QuestRepository questRepository;
    private PlayerQuestRepository playerQuestRepository;
    private QuestRegistryImpl quests;
    private ObjectiveRegistryImpl objectiveTypes;
    private RewardRegistryImpl rewardTypes;
    private ProgressService progressService;
    private RewardService rewardService;
    private ProgressDisplay progressDisplay;
    private MoneyReward moneyReward;
    private PointsReward pointsReward;
    private DailyService dailyService;
    private EditorServer editorServer;
    private cn.yvmou.ylib.scheduler.UniversalTask actionBarTask;
    private cn.yvmou.ylib.scheduler.UniversalTask dailyTask;

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

        // 包一层：YLib 消息服务只转 & 颜色码、不解析 MiniMessage，
        // 而本项目要求「MiniMessage 为主、兼容 &」，因此在出口处统一渲染
        messages = new LangMessageService(ylib.createMessageService(MessageSettings.builder()
                .defaultLanguage(config.getLanguageDefault())
                .availableLanguages(config.getLanguageAvailable().toArray(new String[0]))
                // 默认文件模式是 lang_%s.yml，这里改用更贴近惯例的 <code>.yml
                .filePattern("%s.yml")
                .languageFolder("lang")
                .useClientLocale(config.isLanguageUseClientLocale())
                .build()));

        // ---------- 存储 ----------
        try {
            database = DatabaseFactory.open(config, getDataFolder());
            questRepository = database.questRepository();
            playerQuestRepository = database.playerQuestRepository();
            log.info("存储已就绪: {}", database.description());
        } catch (RuntimeException e) {
            log.error("数据库打开失败，插件将被禁用: {}", e.getMessage());
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
        rewardService = new RewardService(quests, rewardTypes, playerQuestRepository);
        progressDisplay = new ProgressDisplay(config, quests, playerQuestRepository, messages, objectiveTypes);
        dailyService = new DailyService(config, quests, playerQuestRepository, progressService);

        // ---------- 任务数据 ----------
        // 读库/写示例任务都可能因磁盘或连接问题失败，单独守护，
        // 让插件以「零任务」状态启动而不是直接崩掉
        guard("示例任务写入", this::seedIfEmpty);
        guard("任务载入", this::reloadQuests);

        // ---------- 事件、命令与调度 ----------
        // 每个子系统独立守护：任何一项失败都应只损失该功能，
        // 而不是让整个插件（乃至服务端启动）失败
        guard("事件监听器", this::registerListeners);
        guard("命令注册", this::registerCommands);
        guard("进度展示调度", this::startActionBarTask);
        guard("每日任务调度", this::startDailyTask);
        guard("网页编辑器", this::startEditor);
        guard("PlaceholderAPI 变量", () ->
                com.playerPlugin.playerTaskX.core.placeholder.PlaceholderHook.register(this));

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
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
        if (dailyTask != null) {
            dailyTask.cancel();
            dailyTask = null;
        }
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

    /**
     * 注册内置目标类型。
     * <p>
     * 这里显式列出而不是扫描包：多几行代码，换来「新增类型必须显式登记」的确定性，
     * 也避免反射扫描在插件类加载器下的兼容问题。
     */
    private void registerBuiltInObjectives() {
        objectiveTypes.register(new BreakBlockObjective());
        objectiveTypes.register(new PlaceBlockObjective());
        objectiveTypes.register(new CraftObjective());
        objectiveTypes.register(new FishObjective());
        objectiveTypes.register(new KillObjective());
        objectiveTypes.register(new ConsumeObjective());
        objectiveTypes.register(new EnchantObjective());
        objectiveTypes.register(new ShearObjective());
        objectiveTypes.register(new BreedObjective());
        objectiveTypes.register(new TameObjective());
        objectiveTypes.register(new InteractObjective());
        objectiveTypes.register(new ChatObjective());
        objectiveTypes.register(new SubmitObjective());
        objectiveTypes.register(new CommandObjective());

        var rejected = objectiveTypes.rejected();
        if (!rejected.isEmpty()) {
            log.warn("有目标类型注册失败: {}", rejected);
        }
    }

    private void registerBuiltInRewards() {
        moneyReward = new MoneyReward();
        pointsReward = new PointsReward();
        rewardTypes.register(moneyReward);
        rewardTypes.register(pointsReward);
        rewardTypes.register(new ExpReward());
        rewardTypes.register(new ItemReward());
        rewardTypes.register(new CommandReward());

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
        // 进度变化后的表现集中在这里：完成时发 title
        java.util.function.Consumer<com.playerPlugin.playerTaskX.core.engine.ApplyResult> onProgress = result -> {
            Player owner = result.playerId() == null ? null : getServer().getPlayer(result.playerId());
            if (owner == null) {
                return;
            }
            for (String questId : result.completedQuests()) {
                Quest quest = quests.find(questId).orElse(null);
                if (quest != null) {
                    progressDisplay.notifyCompletion(owner, quest);
                }
            }
            progressDisplay.update(owner);
        };

        getServer().getPluginManager().registerEvents(new BlockListener(progressService, onProgress), this);
        getServer().getPluginManager().registerEvents(new EntityListener(progressService, onProgress), this);
        getServer().getPluginManager().registerEvents(new ItemListener(progressService, onProgress), this);
        getServer().getPluginManager().registerEvents(
                new PlayerListener(progressService, progressDisplay, this::onPlayerJoin), this);
        // 菜单点击分发：没有它玩家能打开界面但点击无反应
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
    }

    /**
     * 玩家登录：先补发每日任务（可能跨天），再刷新进度展示。
     * <p>
     * 顺序不能颠倒——先展示后补发会让玩家看到空列表。
     */
    private void onPlayerJoin(Player player) {
        if (dailyService.ensureAssigned(player)) {
            messages.send(player, "daily.reset");
        }
        progressDisplay.update(player);
    }

    private void startActionBarTask() {
        if (!config.isActionbarEnabled()) {
            return;
        }
        UniversalScheduler scheduler = ylib.getScheduler();
        long interval = config.getActionbarInterval();
        // 用 YLib 调度器而非 BukkitScheduler：同一份代码在 Folia/Canvas 上也能跑
        actionBarTask = scheduler.runTimer(() -> progressDisplay.updateAll(), interval, interval);
    }

    /**
     * 每日任务：登录时发放 + 定时跨天检查。
     * <p>
     * 定时任务是必需的：玩家挂着不下线时也必须跨天重置，
     * 不能只在登录时判断。
     */
    private void startDailyTask() {
        if (!config.isDailyEnabled()) {
            return;
        }
        UniversalScheduler scheduler = ylib.getScheduler();
        dailyTask = scheduler.runTimer(() -> {
            for (Player player : getServer().getOnlinePlayers()) {
                if (dailyService.ensureAssigned(player)) {
                    messages.send(player, "daily.reset");
                }
            }
        }, 100L, 6000L);
    }

    /**
     * 启动内置网页编辑器。
     * <p>
     * 刻意不做「端口占用就自动换端口」：管理员配了 8080 却实际跑在 8081
     * 比直接失败更难排查。失败只记录日志，不影响插件其它功能。
     */
    private void startEditor() {
        if (!config.isEditorEnabled()) {
            return;
        }
        editorServer = new EditorServer(this);
        editorServer.start(config.getEditorPort());
    }

    /**
     * 空库时写入一个示例任务。
     * <p>
     * 存在的理由：全新安装若一个任务都没有，管理员看不到任何效果也无从对照格式。
     * 示例任务用固定 id {@code example_daily_mine}，可随时删除，且只在库为空时写入，
     * 不会覆盖任何已有数据。
     */
    private void seedIfEmpty() {
        if (questRepository.count() > 0) {
            return;
        }
        Quest example = new Quest(
                "example_daily_mine",
                // MiniMessage 写法不要用闭合标签：</yellow> 属于「未开启标签的闭合」，
                // MiniMessage 会直接抛异常（颜色本来就由后续标签覆盖，无需闭合）
                "<yellow>挖矿日常",
                List.of("<gray>挖掘 64 个石头", "<gray>完成后可领取 500 金币"),
                "STONE_PICKAXE",
                "每日",
                com.playerPlugin.playerTaskX.api.model.QuestType.DAILY,
                List.of(com.playerPlugin.playerTaskX.api.model.QuestObjective.of("break_block",
                        java.util.Map.of("target", "STONE", "amount", 64))),
                List.of(com.playerPlugin.playerTaskX.api.model.QuestReward.of("money",
                        java.util.Map.of("amount", 500))),
                config.getDailyRefreshCost(),
                true);
        questRepository.save(example);
        log.info("数据库为空，已写入示例任务 {}（可自由删除或修改）", example.id());
    }

    /** 从存储载入任务定义到内存注册表。 */
    public void reloadQuests() {
        List<Quest> loaded = questRepository.findAll();
        quests.replaceAll(loaded);
        int skipped = 0;
        for (Quest quest : loaded) {
            if (!quest.isUsable()) {
                log.warn("任务 {} 结构不完整（缺少目标），已跳过", quest.id());
                skipped++;
                continue;
            }
            for (String problem : validate(quest)) {
                log.warn("任务 {} 配置有问题: {}", quest.id(), problem);
            }
        }
        log.info("已载入 {} 个任务{}", quests.all().size(), skipped > 0 ? "（跳过 " + skipped + " 个）" : "");
    }

    /** 校验任务引用的目标与奖励类型是否都可用。 */
    public List<String> validate(Quest quest) {
        List<String> problems = new java.util.ArrayList<>();
        for (var objective : quest.objectives()) {
            if (!objectiveTypes.contains(objective.type())) {
                problems.add("未知目标类型 " + objective.type());
            }
        }
        problems.addAll(rewardService.validate(quest));
        return problems;
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

    /**
     * 把刷新费用渲染成给玩家看的文案，如「1,000 金币」「50 经验」。
     * <p>
     * 金币交给 Vault 的格式化（与服务器经济插件显示一致），
     * 其它货币是整数，直接用其显示名。没有实际扣费结果时（例如按钮文案），
     * 用兜底链挑出的货币。
     *
     * @param result 刷新结果；为 null 时按兜底链推断货币
     */
    public String formatRefreshCost(double cost, DailyService.RefreshResult result) {
        CurrencyType currency = result == null ? CurrencyType.detect() : result.currency();
        if (currency == null) {
            currency = CurrencyType.detect();
        }
        if (currency == CurrencyType.MONEY) {
            return MoneyReward.format(cost);
        }
        return currency.toUnits(cost) + " " + currency.displayName();
    }

    public QuestRepository questRepository() {
        return questRepository;
    }

    public PlayerQuestRepository playerQuestRepository() {
        return playerQuestRepository;
    }

    public YLib ylib() {
        return ylib;
    }

    /** 渲染文本（MiniMessage 优先，兼容 & 颜色码）。 */
    public String text(String raw) {
        return TextRenderer.render(raw);
    }

    /** 存储描述，供编辑器与命令展示。 */
    public String describeStorage() {
        return database == null ? "未连接" : database.description();
    }

    /**
     * 保存任务（新增或覆盖）并刷新内存注册表。
     * <p>
     * 编辑器、管理命令、管理 GUI 都走这一个入口：
     * 「落库 + 更新注册表 + 重建玩家索引」必须成对发生，否则会出现
     * 「库里改了但玩家进度仍按旧定义算」的错位。
     */
    public void saveQuest(Quest quest) {
        questRepository.save(quest);
        quests.upsert(quest);
        for (Player player : getServer().getOnlinePlayers()) {
            progressService.rebuildIndex(player.getUniqueId());
        }
    }

    /** 删除任务，并清理其内存定义。 */
    public boolean deleteQuest(String id) {
        boolean removed = questRepository.delete(id);
        if (removed) {
            quests.remove(id);
            for (Player player : getServer().getOnlinePlayers()) {
                progressService.rebuildIndex(player.getUniqueId());
            }
        }
        return removed;
    }
}
