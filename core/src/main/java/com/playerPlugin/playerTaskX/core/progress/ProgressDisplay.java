package com.playerPlugin.playerTaskX.core.progress;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.registry.QuestRegistry;
import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.text.PlayerNotifier;
import cn.yvmou.ylib.text.TextRenderer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度展示：actionbar 推送进度、完成时发 title。
 *
 * <h2>关于缓存：曾经有过，已删除</h2>
 * 早先这里维护了一份「玩家 → 任务 → 目标 → 当前/需求」的缓存，设计意图是避免每次刷新都读库。
 * 但实际实现里<b>没有任何地方调用过写入方法</b>，于是渲染永远走回退分支，
 * 用调用方传入的（可能已过时的）{@code PlayerQuest} 取进度——
 * 表现为「进度条会涨、{'0/64'} 数字不动」这种自相矛盾的显示。
 * <p>
 * 去掉缓存后改为每次从仓储读取：数据来源唯一，不会出现两份进度不一致。
 * 代价是每次刷新一次查询，因此 {@link #update(Player)} 在查询前先用内存索引
 * 判断该玩家是否有进行中的任务，空闲玩家（大多数情况）不产生任何查询。
 */
public final class ProgressDisplay {

    private final PluginConfig config;
    private final QuestRegistry quests;
    private final PlayerQuestRepository repository;
    private final cn.yvmou.ylib.message.MessageService messages;
    private final com.playerPlugin.playerTaskX.api.registry.ObjectiveRegistry objectiveTypes;
    private cn.yvmou.ylib.scheduler.UniversalTask refreshTask;

    public ProgressDisplay(PluginConfig config, QuestRegistry quests, PlayerQuestRepository repository,
                           cn.yvmou.ylib.message.MessageService messages,
                           com.playerPlugin.playerTaskX.api.registry.ObjectiveRegistry objectiveTypes) {
        this.config = config;
        this.quests = quests;
        this.repository = repository;
        this.messages = messages;
        this.objectiveTypes = objectiveTypes;
    }

    /**
     * 启动 actionbar 定时刷新。轮询间隔与开关是展示层的配置，
     * 因此定时器归展示层自己所有，入口类只负责启停时机。
     */
    public void startAutoRefresh(cn.yvmou.ylib.scheduler.UniversalScheduler scheduler) {
        if (!config.isActionbarEnabled()) {
            return;
        }
        long interval = config.getActionbarInterval();
        refreshTask = scheduler.runTimer(this::updateAll, interval, interval);
    }

    /** 停止 actionbar 定时刷新（插件禁用时调用；未启动过则为空操作）。 */
    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /**
     * 进度变化后的表现：有任务完成则发 title，随后刷新 actionbar。
     * <p>
     * 以方法引用交给各进度监听器（Block/Entity/Item/TextListener），
     * 监听器因此只需要认识 {@code Consumer<ApplyResult>}，不必认识展示层；
     * 「进度变了要做什么」也就只剩这一个出处。
     */
    public void onProgressApplied(com.playerPlugin.playerTaskX.core.engine.ApplyResult result) {
        Player owner = result.playerId() == null ? null : Bukkit.getPlayer(result.playerId());
        if (owner == null) {
            return;
        }
        for (String questId : result.completedQuests()) {
            Quest quest = quests.find(questId).orElse(null);
            if (quest != null) {
                notifyCompletion(owner, quest);
            }
        }
        update(owner);
    }

    /** 完成提醒：title + actionbar。 */
    public void notifyCompletion(Player player, Quest quest) {
        if (!config.isTitleOnComplete()) {
            return;
        }
        // 文案走语言文件（缺失时回退到内置文案），传原始文本给 PlayerNotifier 统一渲染
        String title = messages != null && messages.has("quest.completed-title")
                ? messages.raw("quest.completed-title")
                : "&a✔ 任务完成";
        PlayerNotifier.title(player, title, quest.name(), 10, 50, 10);
    }

    /** 推送一次 actionbar（展示玩家的第一个进行中任务）。 */
    public void update(Player player) {
        if (!config.isActionbarEnabled() || player == null || !player.isOnline()) {
            return;
        }
        String line = buildLine(player);
        if (line == null || line.isEmpty()) {
            return;
        }
        PlayerNotifier.actionBar(player, line);
    }

    /** 为玩家所有在线玩家刷新（定时任务调用）。 */
    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    /**
     * 构建 actionbar 文本：取该玩家第一个进行中的任务。
     * <p>
     * 数据来源只有仓储一处——之前那份「写入方从未被调用」的缓存会让进度条与数字来自
     * 两个不同来源，从而出现「条会涨、数字不动」。
     */
    private String buildLine(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerQuest active = repository.findActiveByPlayer(playerId).stream().findFirst().orElse(null);
        if (active == null) {
            return null;
        }
        Quest quest = quests.find(active.questId()).orElse(null);
        if (quest == null || !quest.enabled()) {
            return null;
        }
        return render(quest, active);
    }

    /**
     * 渲染单个任务的进度行。
     *
     * @param playerQuest 玩家任务记录；为 {@code null} 时按「尚未开始」渲染（管理员预览用）
     */
    public String render(Quest quest, PlayerQuest playerQuest) {
        List<String> objectiveTexts = new ArrayList<>();
        for (int i = 0; i < quest.objectives().size(); i++) {
            QuestObjective objective = quest.objectives().get(i);
            int current = playerQuest == null ? 0 : playerQuest.progress(i);
            int required = objective.amount();
            objectiveTexts.add(label(objective.type()) + " " + current + "/" + required);
        }

        double ratio = playerQuest == null ? 0.0 : playerQuest.completionRatio(quest);

        // 只组装「原文」（任务名保持原始写法，可能是 MiniMessage 标签或 & 码），
        // 最后统一渲染一次：先渲染再拼接会让 § 码在二次渲染时被当作普通字符而失效
        String detail = String.join(" &7| &f", objectiveTexts);
        int percent = (int) Math.round(ratio * 100);
        String bar = progressBar(ratio, 20);

        return TextRenderer.render(quest.name() + " &7" + bar + " &f" + percent + "% &8» &7" + detail);
    }

    /**
     * 目标显示名。
     * <p>
     * 取自语言文件的 {@code objective.<type>} 键（用户可自定义措辞），
     * 缺失时退回目标类型自带的显示名，再不行才显示原始 id——
     * 绝不把 {@code break_block} 这种内部标识直接抛给玩家。
     */
    private String label(String objectiveType) {
        if (messages != null) {
            String key = "objective." + objectiveType;
            if (messages.has(key)) {
                return TextRenderer.strip(messages.raw(key));
            }
        }
        return objectiveTypes.find(objectiveType)
                .map(ObjectiveType::displayName)
                .orElse(objectiveType);
    }

    /** 生成进度条，用 MiniMessage 的颜色标签表达已完成的长度。 */
    private String progressBar(double ratio, int length) {
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, ratio)) * length);
        StringBuilder builder = new StringBuilder("&a");
        for (int i = 0; i < length; i++) {
            if (i == filled) {
                builder.append("&8");
            }
            builder.append('|');
        }
        return builder.toString();
    }
}
