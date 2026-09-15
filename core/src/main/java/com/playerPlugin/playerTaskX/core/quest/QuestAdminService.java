package com.playerPlugin.playerTaskX.core.quest;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.integration.customcontent.CustomContentHooks;
import com.playerPlugin.playerTaskX.core.integration.customfishing.CustomFishingHook;
import com.playerPlugin.playerTaskX.core.integration.customfishing.FishLoot;
import com.playerPlugin.playerTaskX.core.integration.mythicmobs.MythicMobsHook;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.engine.RewardService;
import com.playerPlugin.playerTaskX.core.schema.ValueKinds;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import com.playerPlugin.playerTaskX.core.preset.PresetRefs;

/**
 * 任务定义的维护入口：保存、删除、重载、校验、启停，管理命令与管理 GUI 共用这一处。
 * 「改一个任务」要同时落库、更新内存注册表、重建玩家进度索引，三者成对发生的不变量由本类担保；校验与措辞也只有这一处。
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

    /** 从存储载入全部任务定义（缺目标的跳过、类型不可用的只记警告），并展开预设引用后重建在线玩家进度索引——展开会改变目标结构，不重建就会按旧定义算。 */
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

    /** 校验任务定义并返回问题清单（空表示无问题）：类型不仅要存在还要可用，否则例如 Vault 未装时金币奖励配置合法却一分钱也发不出去。 */
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
            } else {
                // 字段值域：配了「永远不可能命中」的值必须报出来（挖苹果、剪猪毛…）。
                // 判据与 GUI 推图标读的是同一份声明（见 ValueKind / ValueKinds），
                // 因此「GUI 里摆不出这个图标」与「校验会报问题」天然一致
                problems.addAll(valueProblems(type, objective));
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

    /** 字段值域校验：值可能手打或来自 {@code quests/*.yml}，这类错误的表现是「配了却永远不涨进度」，因此必须逐条报出来；CustomFishing 没装时没有鱼 id 清单，只能放行。 */
    private List<String> valueProblems(ObjectiveType type, QuestObjective objective) {
        List<ValueKind> allKinds = new ArrayList<>();
        boolean needsFish = false;
        for (ConfigField field : type.schema()) {
            if (field.kinds().isEmpty()) {
                continue;
            }
            allKinds.addAll(field.kinds());
            needsFish = needsFish || field.kinds().contains(ValueKind.FISH);
        }
        if (allKinds.isEmpty()) {
            return List.of();
        }
        List<String> fishLoot = needsFish && CustomFishingHook.supported()
                ? CustomFishingHook.loot().stream().map(FishLoot::id).toList()
                : null;

        List<String> problems = new ArrayList<>();
        for (ConfigField field : type.schema()) {
            if (field.kinds().isEmpty()) {
                continue;
            }
            String problem = ValueKinds.check(field.kinds(), objective.string(field.key(), ""), fishLoot);
            if (problem != null) {
                problems.add(problem);
            }
        }
        return problems;
    }

    /** 保存任务（新增或覆盖）并同步三处状态：「落库 + 更新注册表 + 重建玩家索引」必须成对发生，且预设引用要先 {@link PresetRefs#trim} 再 {@link PresetRefs#resolve}，顺序不能反。 */
    public void save(Quest quest) {
        Quest trimmed = PresetRefs.trim(quest);
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

    /** 切换任务的启用状态并落库，返回更新后的任务（id 不存在时返回 {@code null}）；刻意走单条 upsert 而不是全量 {@link #reload()}。 */
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
