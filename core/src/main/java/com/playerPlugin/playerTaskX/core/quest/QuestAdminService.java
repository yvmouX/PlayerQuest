package com.playerPlugin.playerTaskX.core.quest;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.reward.RewardService;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * 任务定义的维护入口：保存、删除、重载、校验。
 *
 * <h2>为什么它存在</h2>
 * 「改一个任务」要同时动三处——落库、更新内存注册表、重建玩家进度索引——
 * 三者必须成对发生，否则会出现「库里改了但玩家进度仍按旧定义算」的错位。
 * 管理命令、管理 GUI、网页编辑器后台都走这一个入口，成对不变量由本类担保，
 * 而不是散落在各个调用点靠调用方自觉。
 *
 * <h2>它不在 PlayerTaskX 里</h2>
 * 入口类承诺「只装配、不含业务逻辑」，这四个操作是货真价实的领域逻辑，
 * 寄居在装配类里会让入口类成为所有东西的依赖黑洞。拆出后本类可以
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

    public QuestAdminService(QuestRepository repository, QuestRegistryImpl quests,
                             ObjectiveRegistryImpl objectiveTypes, RewardService rewardService,
                             ProgressService progressService, Supplier<Collection<UUID>> onlinePlayerIds) {
        this.repository = repository;
        this.quests = quests;
        this.objectiveTypes = objectiveTypes;
        this.rewardService = rewardService;
        this.progressService = progressService;
        this.onlinePlayerIds = onlinePlayerIds;
    }

    /**
     * 从存储载入全部任务定义到内存注册表，并顺带校验引用完整性。
     * <p>
     * 结构不完整（缺目标）的任务跳过而不让整个载入失败；
     * 引用了不存在/不可用类型的任务照常载入但记警告——配置问题
     * 不该让其它任务一起不可用。
     */
    public void reload() {
        List<Quest> loaded = repository.findAll();
        quests.replaceAll(loaded);
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

    /** 校验任务引用的目标与奖励类型是否都可用，返回问题清单（空表示无问题）。 */
    public List<String> validate(Quest quest) {
        List<String> problems = new ArrayList<>();
        for (var objective : quest.objectives()) {
            if (!objectiveTypes.contains(objective.type())) {
                problems.add("未知目标类型 " + objective.type());
            }
        }
        problems.addAll(rewardService.validate(quest));
        return problems;
    }

    /**
     * 空库时写入一批出厂示例任务（清单见 ExampleQuests）。
     * <p>
     * 只在库为空时写入，绝不覆盖已有数据；示例统一用 {@code example_} 前缀，可随时删除。
     * 写入走 {@link #save} 而不是裸写仓储：示例也要同步进注册表与索引。
     */
    public void seedIfEmpty(List<Quest> examples) {
        if (repository.count() > 0 || examples.isEmpty()) {
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
     */
    public void save(Quest quest) {
        repository.save(quest);
        quests.upsert(quest);
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
     * 重建所有在线玩家的进度索引。
     * <p>
     * 只重建在线玩家：离线玩家的索引会在登录时按新定义重建（见 PlayerListener）。
     */
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
