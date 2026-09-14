package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.core.quest.QuestAdminService;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.RewardRegistryImpl;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.PresetRepository;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;

import java.util.function.Supplier;

/**
 * {@link EditorServices} 的插件侧实现：把主类持有的那几个子系统转交给编辑器。
 *
 * <h2>为什么单独一个类，而不是让主类 {@code implements EditorServices}</h2>
 * 主类曾经直接实现这个接口，代价是「网页编辑器需要什么」成了主类公开契约的一部分：
 * 为了让编辑器拿到预设仓储与存储描述，主类必须多出两个<b>只有 web 层会用到</b>的 getter
 * （{@code presets()} 与 {@code describeStorage()}）。主类的公开面应当只回答
 * 「游戏内功能（命令 / GUI / 变量）需要什么」，编辑器的需求不该混在里面。
 * 现在这两个值由本类在构造时注入，主类里它们仍是私有字段。
 *
 * <h2>为什么不是「只拿插件实例、再转调它的 getter」</h2>
 * 那样等于把刚摘掉的两个 getter 又加回去，只是换了调用方——少一个构造参数，却把公开面还了回去。
 * 现在的代价只是「编辑器将来要新能力时，本类的构造参数要多一个」；而 {@link EditorServices}
 * 本身就是窄接口，加参数这件事无论如何都要过一遍。
 *
 * <h2>它不认识插件生命周期</h2>
 * 只持有七个仓储 / 服务与一个「存储描述」的取值函数，不持有 {@code PlayerTaskX}，
 * 因此也没有把 Bukkit 带进这一层：{@link EditorApi} 拿到的仍然只是一个窄接口。
 * 需要插件实例的部分（端口、令牌、静态资源、日志）留在 {@link EditorServer}——
 * 那是装配层，本来就该拿具体类型。
 */
public final class PluginEditorServices implements EditorServices {

    private final QuestRegistryImpl quests;
    private final QuestRepository questDefinitions;
    private final QuestAdminService questAdmin;
    private final PresetRepository presets;
    private final PlayerQuestRepository playerQuestRepository;
    private final ObjectiveRegistryImpl objectiveTypes;
    private final RewardRegistryImpl rewardTypes;
    private final Supplier<String> storageDescription;

    /**
     * @param storageDescription 存储描述（如 {@code SQLite: data/playerTaskX.db}）；
     *                           用取值函数而不是当场取值：数据库句柄在装配过程中才就绪，
     *                           而快照会把这个顺序要求变成一处看不见的约束
     */
    public PluginEditorServices(QuestRegistryImpl quests, QuestRepository questDefinitions,
                                QuestAdminService questAdmin, PresetRepository presets,
                                PlayerQuestRepository playerQuestRepository,
                                ObjectiveRegistryImpl objectiveTypes, RewardRegistryImpl rewardTypes,
                                Supplier<String> storageDescription) {
        this.quests = quests;
        this.questDefinitions = questDefinitions;
        this.questAdmin = questAdmin;
        this.presets = presets;
        this.playerQuestRepository = playerQuestRepository;
        this.objectiveTypes = objectiveTypes;
        this.rewardTypes = rewardTypes;
        this.storageDescription = storageDescription;
    }

    @Override
    public QuestRegistryImpl quests() {
        return quests;
    }

    @Override
    public QuestRepository questDefinitions() {
        return questDefinitions;
    }

    @Override
    public QuestAdminService questAdmin() {
        return questAdmin;
    }

    @Override
    public PresetRepository presets() {
        return presets;
    }

    @Override
    public PlayerQuestRepository playerQuestRepository() {
        return playerQuestRepository;
    }

    @Override
    public ObjectiveRegistryImpl objectiveTypes() {
        return objectiveTypes;
    }

    @Override
    public RewardRegistryImpl rewardTypes() {
        return rewardTypes;
    }

    @Override
    public String describeStorage() {
        // 取值函数可能返回 null（数据库尚未打开），如实降级成「未连接」而不是让前端拿到 null
        String description = storageDescription == null ? null : storageDescription.get();
        return description == null ? "未连接" : description;
    }
}
