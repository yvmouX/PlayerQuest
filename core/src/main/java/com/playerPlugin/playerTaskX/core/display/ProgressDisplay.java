package com.playerPlugin.playerTaskX.core.display;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.registry.ObjectiveRegistry;
import com.playerPlugin.playerTaskX.api.registry.QuestRegistry;
import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.text.PlayerNotifier;
import com.playerPlugin.playerTaskX.core.text.Texts;
import cn.yvmou.ylib.message.MessageService;
import cn.yvmou.ylib.scheduler.UniversalScheduler;
import cn.yvmou.ylib.scheduler.UniversalTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.playerPlugin.playerTaskX.core.engine.ApplyResult;

/**
 * 进度展示：actionbar 推送进度、任务完成时发 title。
 * 进度一律现从仓储读、不做缓存，因此刷新前先用内存索引排掉没有进行中任务的玩家，空闲玩家不产生查询。
 */
public final class ProgressDisplay {

    private final PluginConfig config;
    private final QuestRegistry quests;
    private final PlayerQuestRepository repository;
    private final MessageService messages;
    private final ObjectiveRegistry objectiveTypes;
    private UniversalTask refreshTask;

    public ProgressDisplay(PluginConfig config, QuestRegistry quests, PlayerQuestRepository repository,
                           MessageService messages, ObjectiveRegistry objectiveTypes) {
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
    public void startAutoRefresh(UniversalScheduler scheduler) {
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

    /** 进度变化后的表现：有任务完成则发 title，随后刷新 actionbar；以方法引用交给各进度监听器，监听器只需认识 {@code Consumer<ApplyResult>}。 */
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

        return Texts.render(quest.name() + " &7" + bar + " &f" + percent + "% &8» &7" + detail);
    }

    /** 目标显示名：取语言键 {@code objective.<type>}，缺失时退回类型自带显示名，绝不把 {@code break_block} 这类内部标识抛给玩家。 */
    private String label(String objectiveType) {
        return Texts.typeName(messages, null, "objective", objectiveType,
                objectiveTypes.find(objectiveType).map(ObjectiveType::displayName).orElse(objectiveType));
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
