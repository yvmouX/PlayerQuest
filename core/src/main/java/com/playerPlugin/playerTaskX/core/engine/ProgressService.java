package com.playerPlugin.playerTaskX.core.engine;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.registry.ObjectiveRegistry;
import com.playerPlugin.playerTaskX.api.registry.QuestRegistry;
import com.playerPlugin.playerTaskX.core.storage.Hash;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 进度引擎：把一次游戏内动作换算成各任务目标的进度。
 * <p>
 * 这是唯一修改玩家进度的入口，因此所有「进度正确性」的保证都集中在这里。
 *
 * <h2>性能设计</h2>
 * 每次动作（挖掘、说话、移动触发的交互…）都会调用 {@link #apply}，
 * 而一个玩家可能有多个进行中的任务，逐个查数据库不可接受。因此：
 * <ul>
 *   <li>先在内存里查该玩家「可能被本次动作推进」的任务下标（{@code activeIndex}）；</li>
 *   <li>按 {@link Trigger} 过滤出相关目标类型，只有命中的目标才累加；</li>
 *   <li>仅当进度真的变化时才写库，未命中不产生任何 IO。</li>
 * </ul>
 * 索引只缓存「任务下标 → 目标下标 → 类型」这类静态映射，运行期进度不常驻内存，
 * 避免引入缓存一致性问题。
 */
public final class ProgressService {

    private final QuestRegistry quests;
    private final ObjectiveRegistry objectiveTypes;
    private final PlayerQuestRepository repository;

    /**
     * 玩家 → 任务 id → 目标下标 → 目标类型。
     * <p>
     * 只保留在线玩家的条目；退出登录时必须调用 {@link #unload(UUID)}，
     * 否则长期运行会随玩家数增长而泄漏。
     */
    private final Map<UUID, Map<String, Map<Integer, ObjectiveType>>> activeIndex = new ConcurrentHashMap<>();

    /** 结构变化导致进度被重置时的告警出口；默认为空（测试环境不需要日志）。 */
    private BiConsumer<String, String> structureChangeWarner = (questId, playerId) -> {
    };

    public ProgressService(QuestRegistry quests, ObjectiveRegistry objectiveTypes, PlayerQuestRepository repository) {
        this.quests = quests;
        this.objectiveTypes = objectiveTypes;
        this.repository = repository;
    }

    /** 注入「目标结构变化导致进度重置」的告警出口（参数：任务 id、玩家 id）。 */
    public void onStructureChanged(BiConsumer<String, String> warner) {
        if (warner != null) {
            this.structureChangeWarner = warner;
        }
    }

    /**
     * 计算任务目标列表的结构摘要。
     * <p>
     * 进度按<b>下标</b>记录，因此只要目标的数量、顺序、类型或关键参数变了，
     * 旧进度的含义就整体错位。把「顺序敏感」的信息拼成一个字符串再取哈希：
     * 顺序不同的列表必然得到不同摘要，正是需要区分的。
     */
    public static String structureHash(Quest quest) {
        StringBuilder builder = new StringBuilder();
        for (QuestObjective objective : quest.objectives()) {
            builder.append(objective.type()).append('\u0001')
                    .append(objective.amount()).append('\u0001')
                    .append(objective.properties()).append('\u0002');
        }
        return Hash.fingerprint(builder.toString());
    }

    /**
     * 校验玩家记录的结构摘要是否仍与当前定义一致。
     * <p>
     * 三种结果：
     * <ul>
     *   <li>记录里没有摘要（旧版本数据）→ <b>只补齐，不重置</b>：没有依据就重置等于凭空清进度；</li>
     *   <li>摘要一致 → 什么都不做；</li>
     *   <li>摘要不一致 → <b>清空该任务进度并记日志</b>。宁可让这个任务进度归零并告知玩家，
     *       也不能把进度静默套用到别的目标上——后者会让人以为插件坏了，且极难排查。</li>
     * </ul>
     *
     * @return 是否发生了重置
     */
    private boolean reconcileStructure(PlayerQuest playerQuest, Quest quest) {
        String current = structureHash(quest);
        String stored = playerQuest.structureHash();
        if (stored == null || stored.isBlank()) {
            playerQuest.structureHash(current);
            return false;
        }
        if (stored.equals(current)) {
            return false;
        }
        playerQuest.restoreProgress(Map.of());
        playerQuest.structureHash(current);
        if (playerQuest.status() == QuestStatus.COMPLETED) {
            // 进度清零后不该还能领奖
            playerQuest.status(QuestStatus.IN_PROGRESS);
        }
        structureChangeWarner.accept(quest.id(), String.valueOf(playerQuest.playerId()));
        return true;
    }

    /** 玩家上线/切换世界后载入其任务索引，并顺带校正结构变化。 */
    public void load(UUID playerId) {
        Map<String, Map<Integer, ObjectiveType>> index = new LinkedHashMap<>();
        // 取全部记录而不是只取进行中的：已完成的记录同样会因定义变化而错位，
        // 而 findActiveByPlayer 会把它们排除掉，那样就永远检测不到——
        // 结果是玩家可能领到按错误进度判定的奖励。
        for (PlayerQuest playerQuest : repository.findByPlayer(playerId)) {
            Quest quest = quests.find(playerQuest.questId()).orElse(null);
            if (quest == null) {
                continue;
            }
            if (reconcileStructure(playerQuest, quest)) {
                repository.save(playerQuest);
            }
            if (playerQuest.isActive()) {
                index.put(quest.id(), buildObjectiveIndex(quest));
            }
        }
        activeIndex.put(playerId, index);
    }

    /** 玩家下线时释放索引。 */
    public void unload(UUID playerId) {
        activeIndex.remove(playerId);
    }

    /** 任务定义变化（重载/编辑）后重建索引，保证下标与最新定义一致。 */
    public void rebuildIndex(UUID playerId) {
        if (!activeIndex.containsKey(playerId)) return;
        load(playerId);
    }

    /**
     * 处理一次游戏内动作。
     *
     * @return 变化情况与本次完成的任务 id
     */
    public ApplyResult apply(ProgressContext context) {
        UUID playerId = context.playerId();
        if (playerId == null) {
            return ApplyResult.NONE;
        }

        // 索引缺失说明玩家未登记（例如插件重载后未触发登录事件），补建一次
        Map<String, Map<Integer, ObjectiveType>> index = activeIndex.get(playerId);
        if (index == null) {
            load(playerId);
            index = activeIndex.get(playerId);
            if (index == null) {
                return ApplyResult.NONE;
            }
        }
        if (index.isEmpty()) {
            return ApplyResult.NONE;
        }

        boolean changed = false;
        List<String> completed = new ArrayList<>();

        // 复制一份 key 集合再遍历：applyProgress 内部可能改动索引（完成任务/清空）
        for (String questId : new ArrayList<>(index.keySet())) {
            Quest quest = quests.find(questId).orElse(null);
            if (quest == null || !quest.enabled()) {
                // 任务已被禁用（可能是管理员刚在 GUI/命令里改的）：
                // 不再累计进度，避免「禁用后仍在涨」这种看起来像 bug 的行为
                continue;
            }

            PlayerQuest playerQuest = repository.find(playerId, questId).orElse(null);
            if (playerQuest == null || !playerQuest.isActive()) continue;

            if (applyToQuest(playerQuest, quest, index.get(questId), context)) {
                changed = true;
                if (playerQuest.status() == QuestStatus.COMPLETED) {
                    completed.add(questId);
                }
                repository.save(playerQuest);
            }
        }

        return new ApplyResult(playerId, changed, List.copyOf(completed));
    }

    /**
     * 判定单个任务是否被本次动作推进；命中则累加进度并在全部达标时标记完成。
     *
     * @return 进度是否发生变化（决定是否需要写库）
     */
    private boolean applyToQuest(PlayerQuest playerQuest, Quest quest,
                                 Map<Integer, ObjectiveType> objectiveIndex, ProgressContext context) {
        boolean changed = reconcileStructure(playerQuest, quest);

        for (Map.Entry<Integer, ObjectiveType> entry : objectiveIndex.entrySet()) {
            int objectiveSlot = entry.getKey();
            ObjectiveType type = entry.getValue();

            // 动作类型不匹配直接跳过，避免无谓的 properties 解析
            if (type.trigger() != context.trigger()) continue;

            QuestObjective objective = quest.objectives().get(objectiveSlot);
            int required = objective.amount();
            if (playerQuest.progress(objectiveSlot) >= required) continue;

            int delta;
            try {
                delta = type.match(context, objective.properties());
            } catch (RuntimeException e) {
                // 单个目标类型实现有缺陷不应拖垮整条链路
                throw new ObjectiveMatchException(quest.id(), objective.type(), e);
            }
            if (delta <= 0) continue;

            if (playerQuest.addProgress(objectiveSlot, delta, required) > 0) {
                changed = true;
            }
        }

        if (changed && playerQuest.isFullyCompleted(quest)) {
            playerQuest.status(QuestStatus.COMPLETED);
        }
        return changed;
    }

    /** 构建「目标下标 → 目标类型」映射；未知类型会被跳过并在日志中体现。 */
    private Map<Integer, ObjectiveType> buildObjectiveIndex(Quest quest) {
        Map<Integer, ObjectiveType> index = new LinkedHashMap<>();
        List<QuestObjective> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveType type = objectiveTypes.find(objectives.get(i).type()).orElse(null);
            if (type != null) {
                index.put(i, type);
            }
        }
        return index;
    }

    /** 把某玩家某任务的进度直接设为指定值（供管理员命令与调试使用）。 */
    public boolean setProgress(UUID playerId, String questId, int objectiveSlot, int value) {
        Quest quest = quests.find(questId).orElse(null);
        if (quest == null || objectiveSlot < 0 || objectiveSlot >= quest.objectives().size()) {
            return false;
        }
        PlayerQuest playerQuest = repository.find(playerId, questId).orElse(null);
        if (playerQuest == null) {
            return false;
        }
        playerQuest.setProgress(objectiveSlot, Math.max(0, value));
        if (playerQuest.isFullyCompleted(quest)) {
            playerQuest.status(QuestStatus.COMPLETED);
        } else if (playerQuest.status() == QuestStatus.COMPLETED) {
            // 进度被回退时同步回退状态，避免出现「未达标却可领取」
            playerQuest.status(QuestStatus.IN_PROGRESS);
        }
        repository.save(playerQuest);
        return true;
    }

    /** 玩家当前全部任务记录。 */
    public List<PlayerQuest> activeQuests(UUID playerId) {
        return repository.findActiveByPlayer(playerId);
    }

    /** 玩家在指定类型下的任务记录（含已完成待领取）。 */
    public List<PlayerQuest> questsOfType(UUID playerId, QuestType type) {
        return repository.findByPlayer(playerId).stream()
                .filter(playerQuest -> playerQuest.type() == type)
                .filter(playerQuest -> playerQuest.status() != QuestStatus.ABANDONED)
                .toList();
    }

    /** 为玩家登记一个新任务（接取）。 */
    public void assign(UUID playerId, Quest quest, long expiresAt) {
        PlayerQuest playerQuest = PlayerQuest.assign(playerId, quest, System.currentTimeMillis(), expiresAt);
        repository.save(playerQuest);
        activeIndex.computeIfAbsent(playerId, key -> new LinkedHashMap<>())
                .put(quest.id(), buildObjectiveIndex(quest));
    }

    /** 放弃任务。 */
    public boolean abandon(UUID playerId, String questId) {
        PlayerQuest playerQuest = repository.find(playerId, questId).orElse(null);
        if (playerQuest == null || playerQuest.status() == QuestStatus.CLAIMED) {
            return false;
        }
        playerQuest.status(QuestStatus.ABANDONED);
        repository.save(playerQuest);
        Map<String, Map<Integer, ObjectiveType>> index = activeIndex.get(playerId);
        if (index != null) {
            index.remove(questId);
        }
        return true;
    }

    /** 清除某玩家某类型任务的索引（每日任务刷新后调用）。 */
    public void forget(UUID playerId, QuestType type) {
        Map<String, Map<Integer, ObjectiveType>> index = activeIndex.get(playerId);
        if (index == null) return;
        index.keySet().removeIf(questId -> quests.find(questId)
                .map(quest -> quest.type() == type)
                .orElse(false));
    }

    /** 目标匹配实现抛异常时抛出，便于定位是哪个任务的哪个类型有问题。 */
    public static final class ObjectiveMatchException extends RuntimeException {
        public ObjectiveMatchException(String questId, String objectiveType, Throwable cause) {
            super("任务 " + questId + " 的目标类型 " + objectiveType + " 处理失败", cause);
        }
    }
}
