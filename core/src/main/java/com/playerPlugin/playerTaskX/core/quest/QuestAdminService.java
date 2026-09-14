package com.playerPlugin.playerTaskX.core.quest;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.integration.CustomContentHooks;
import com.playerPlugin.playerTaskX.core.integration.MythicMobsHook;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.reward.RewardService;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * 任务定义的维护入口：保存、删除、重载、校验、启停。
 *
 * <h2>为什么它存在</h2>
 * 「改一个任务」要同时动三处——落库、更新内存注册表、重建玩家进度索引——
 * 三者必须成对发生，否则会出现「库里改了但玩家进度仍按旧定义算」的错位。
 * 管理命令、管理 GUI、网页编辑器后台都走这一个入口，成对不变量由本类担保，
 * 而不是散落在各个调用点靠调用方自觉。
 *
 * <h2>校验也只有这一处</h2>
 * 编辑器标红、管理员命令 {@code /ptxa list}、管理界面上的「! 」提示都调用
 * {@link #validate(Quest)}。三个入口各写一份时，「未知奖励类型」在一处报、
 * 「奖励不可用」在另一处报，管理员就会看到自相矛盾的结论。
 *
 * <h2>它不在 PlayerTaskX 里</h2>
 * 入口类承诺「只装配、不含业务逻辑」，这些是货真价实的领域逻辑。拆出后本类可以
 * 脱离服务端单测（在线玩家列表通过 {@link Supplier} 注入）。
 */
public final class QuestAdminService {

    private final QuestRepository repository;
    private final QuestRegistryImpl quests;
    private final ObjectiveRegistryImpl objectiveTypes;
    private final RewardService rewardService;
    private final ProgressService progressService;
    /** 在线玩家 id 供应器；抽出成 Supplier 是为了让 rebuild 的触发时机可测试。 */
    private final Supplier<Collection<UUID>> onlinePlayerIds;
    /** 按 id 查预设：展开任务里的预设引用用（见 {@link PresetRefs}）。 */
    private final Function<String, Preset> presets;
    /**
     * 自定义内容来源：校验 {@code itemsadder:} / {@code craftengine:} 目标时问它有没有接上。
     * <p>
     * 用 Supplier 而不是直接持有：接入点在装配流程里比本服务晚一步创建
     * （要等软依赖探测），直接持有的话这里永远拿到「还没接上」的空实现。
     */
    private final Supplier<CustomContentHooks> customContent;

    public QuestAdminService(QuestRepository repository, QuestRegistryImpl quests,
                             ObjectiveRegistryImpl objectiveTypes, RewardService rewardService,
                             ProgressService progressService,
                             Supplier<Collection<UUID>> onlinePlayerIds, Function<String, Preset> presets) {
        this(repository, quests, objectiveTypes, rewardService, progressService,
                onlinePlayerIds, presets, CustomContentHooks::empty);
    }

    public QuestAdminService(QuestRepository repository, QuestRegistryImpl quests,
                             ObjectiveRegistryImpl objectiveTypes, RewardService rewardService,
                             ProgressService progressService,
                             Supplier<Collection<UUID>> onlinePlayerIds, Function<String, Preset> presets,
                             Supplier<CustomContentHooks> customContent) {
        this.repository = repository;
        this.quests = quests;
        this.objectiveTypes = objectiveTypes;
        this.rewardService = rewardService;
        this.progressService = progressService;
        this.onlinePlayerIds = onlinePlayerIds;
        this.presets = presets;
        this.customContent = customContent == null ? CustomContentHooks::empty : customContent;
    }

    /**
     * 从存储载入全部任务定义到内存注册表，并顺带校验引用完整性。
     * <p>
     * 结构不完整（缺目标）的任务跳过而不让整个载入失败；
     * 引用了不存在/不可用类型的任务照常载入但记警告——配置问题
     * 不该让其它任务一起不可用。
     * <p>
     * 载入时会展开任务里的预设引用；因为这个展开结果可能改变目标的类型（结构指纹随之变化），
     * 在线玩家的进度索引在这里也一并重建——否则「改了预设」之后，在线玩家的进度会按旧定义算。
     */
    public void reload() {
        List<Quest> loaded = new ArrayList<>();
        for (Quest quest : repository.findAll()) {
            // 载入时就展开预设引用：注册表里放的永远是「生效值」，引擎不必知道预设的存在
            loaded.add(PresetRefs.resolve(quest, presets));
        }
        quests.replaceAll(loaded);
        rebuildIndexes();
        int skipped = 0;
        for (Quest quest : loaded) {
            if (!quest.isUsable()) {
                log(Level.WARNING, "任务 " + quest.id() + " 结构不完整（缺少目标），已跳过");
                skipped++;
                continue;
            }
            for (String problem : validate(quest)) {
                log(Level.WARNING, "任务 " + quest.id() + " 配置有问题: " + problem);
            }
        }
        log(Level.INFO, "已载入 " + quests.all().size() + " 个任务"
                + (skipped > 0 ? "（跳过 " + skipped + " 个）" : ""));
    }

    /**
     * 校验任务定义，返回问题清单（空表示无问题）。
     * <p>
     * 奖励除了「类型是否存在」还要看「是否可用」：Vault 未装时金币奖励配置完全合法，
     * 但玩家一分钱也拿不到——这种情况必须暴露在管理员视图里，否则只能靠翻日志发现。
     * 目标同理（{@link ObjectiveType#available()}），例如没装 CustomFishing 时
     * 「自定义钓鱼」目标永远不涨进度。
     */
    public List<String> validate(Quest quest) {
        List<String> problems = new ArrayList<>();
        if (quest.objectives().isEmpty()) {
            problems.add("任务没有配置任何目标");
        }
        for (var objective : quest.objectives()) {
            if (objective.presetId() != null && objective.type().isBlank()) {
                // 引用的预设不存在：具体原因由 PresetRefs 报，这里不再补一句「未知目标类型 」的空名字
                continue;
            }
            ObjectiveType type = objectiveTypes.find(objective.type()).orElse(null);
            if (type == null) {
                problems.add("未知目标类型 " + objective.type());
            } else if (!type.available()) {
                problems.add("目标类型 " + objective.type() + " 不可用（" + type.unavailableReason() + "）");
            }
        }
        // target 里写了 mythic:<怪物id> 但服务端没有 MythicMobs：这些目标永远命中不了
        problems.addAll(MythicMobsHook.targetProblems(quest));
        // 预设引用写错/被删/类别不对：目标或奖励实际不生效，但表面上任务还在
        problems.addAll(PresetRefs.problems(quest, presets));
        // itemsadder: / craftengine: 目标在没装对应插件（或接入失败）时永远命中不了
        problems.addAll(customContent.get().problems(quest));
        problems.addAll(rewardService.validate(quest));
        return problems;
    }

    /**
     * 空库时写入一批出厂示例任务（清单见 {@code ExampleQuests}）。
     * <p>
     * 问的是<b>库</b>是否为空（{@link QuestRepository#databaseEmpty()}），不是合并后的数量：
     * {@code quests/} 里那套示例（{@code ExampleFiles} 铺的，id 前缀不同）是可并存的另一份，
     * 拿总数判断会让库里这套永远写不进去。绝不覆盖已有数据；示例统一用 {@code example_} 前缀，
     * 可随时删除。
     */
    public void seedIfEmpty(List<Quest> examples) {
        if (!repository.databaseEmpty() || examples.isEmpty()) {
            return;
        }
        for (Quest example : examples) {
            save(example);
        }
        log(Level.INFO, "数据库为空，已写入 " + examples.size() + " 个示例任务（可自由删除或修改）");
    }

    /**
     * 保存任务（新增或覆盖）并同步三处状态。
     * <p>
     * 「落库 + 更新注册表 + 重建玩家索引」必须成对发生：
     * 注册表是引擎与界面的数据源，索引决定玩家进度记到哪个任务定义上。
     * <p>
     * 预设引用在这里处理两次，顺序不能反：先 {@link PresetRefs#trim} 把生效值瘦身回
     * 「作者写的那份」（否则编辑器回传的展开值会被当成显式覆盖写死），再
     * {@link PresetRefs#resolve} 展开成生效值进注册表。
     */
    public void save(Quest quest) {
        Quest trimmed = PresetRefs.trim(quest, presets);
        repository.save(trimmed);
        quests.upsert(PresetRefs.resolve(trimmed, presets));
        rebuildIndexes();
    }

    /** 删除任务；只有确实删掉了才清理内存并重建索引。 */
    public boolean delete(String id) {
        boolean removed = repository.delete(id);
        if (removed) {
            quests.remove(id);
            rebuildIndexes();
        }
        return removed;
    }

    /**
     * 切换任务的启用状态并落库。
     * <p>
     * 管理命令与管理界面共用这一处：两边各写一份时，一边走全量 {@code reload()}、
     * 一边走单条 {@code upsert()}，行为差异没有任何理由，只是重复实现的副产品。
     * 这里刻意选单条 upsert——连点几次开关就触发多次全量读库，代价与收益不成比例；
     * 需要全量重载时另有 {@link #reload()}。
     *
     * @return 更新后的任务；id 不存在时返回 {@code null}
     */
    public Quest setEnabled(String id, boolean enabled) {
        Quest quest = quests.find(id).orElse(null);
        if (quest == null) {
            return null;
        }
        Quest updated = quest.withEnabled(enabled);
        save(updated);
        return updated;
    }

    /** 重建所有在线玩家的进度索引（离线玩家在登录时按新定义重建）。 */
    private void rebuildIndexes() {
        for (UUID playerId : onlinePlayerIds.get()) {
            progressService.rebuildIndex(playerId);
        }
    }

    /** 服务内日志统一走 Bukkit 根日志器并带插件前缀，与 RewardService 一致。 */
    private void log(Level level, String message) {
        Bukkit.getLogger().log(level, "[PlayerTaskX] " + message);
    }
}
