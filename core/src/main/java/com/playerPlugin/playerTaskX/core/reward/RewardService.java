package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.registry.QuestRegistry;
import com.playerPlugin.playerTaskX.api.registry.RewardRegistry;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.core.quest.PrerequisiteService;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.QuestClaimRepository;
import cn.yvmou.ylib.message.MessageService;
import cn.yvmou.ylib.text.TextRenderer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 奖励发放：领取校验、逐个发放、状态落库。
 * <p>
 * 关键取舍：**单个奖励失败不影响其它奖励**。一个配置错误的物品奖励
 * 不应该让玩家连金币也拿不到。
 *
 * <h2>领取时为什么要再查一次前置</h2>
 * 每日抽取时已经把前置未满足的任务挡在池外，但「已发给玩家的任务」会因为管理员改定义
 * 而变成锁定状态（给任务补了个前置）。此时再放行就等于前置形同虚设，
 * 因此这里把同一套判定再走一遍——把关不嫌多，漏一次的代价是任务链被绕过。
 */
public final class RewardService {

    private final QuestRegistry quests;
    private final RewardRegistry rewardTypes;
    private final PlayerQuestRepository repository;
    private final QuestClaimRepository claims;
    private final PrerequisiteService prerequisites;

    public RewardService(QuestRegistry quests, RewardRegistry rewardTypes, PlayerQuestRepository repository,
                         QuestClaimRepository claims, PrerequisiteService prerequisites) {
        this.quests = quests;
        this.rewardTypes = rewardTypes;
        this.repository = repository;
        this.claims = claims;
        this.prerequisites = prerequisites;
    }

    /**
     * 领取任务奖励。
     *
     * @return 领取结果；失败原因由 {@link ClaimOutcome.Status} 给出，调用方不必自己猜
     */
    public ClaimOutcome claim(Player player, String questId) {
        Quest quest = quests.find(questId).orElse(null);
        if (quest == null) {
            return new ClaimOutcome(ClaimOutcome.Status.NOT_ASSIGNED, "", List.of());
        }
        PlayerQuest playerQuest = repository.find(player.getUniqueId(), questId).orElse(null);
        if (playerQuest == null) {
            return new ClaimOutcome(ClaimOutcome.Status.NOT_ASSIGNED, rawName(quest), List.of());
        }
        if (playerQuest.status() == QuestStatus.CLAIMED) {
            // 重复领取会被这里挡住
            return new ClaimOutcome(ClaimOutcome.Status.ALREADY_CLAIMED, rawName(quest), List.of());
        }
        if (playerQuest.status() != QuestStatus.COMPLETED) {
            return new ClaimOutcome(ClaimOutcome.Status.NOT_COMPLETED, rawName(quest), List.of());
        }
        List<String> missing = prerequisites.unsatisfied(player.getUniqueId(), quest);
        if (!missing.isEmpty()) {
            return new ClaimOutcome(ClaimOutcome.Status.LOCKED, rawName(quest), displayNames(missing));
        }

        grant(player, quest);

        playerQuest.status(QuestStatus.CLAIMED);
        // 状态与永久账本一起落库：只写状态的话，跨天重置后前置判定就丢了依据；
        // 只写账本的话，玩家能重复领奖
        repository.transaction(() -> {
            repository.save(playerQuest);
            claims.markClaimed(player.getUniqueId(), quest.id(), System.currentTimeMillis());
        });
        return new ClaimOutcome(ClaimOutcome.Status.CLAIMED, rawName(quest), List.of());
    }

    /** 只发放奖励、不改状态，供管理员补发使用。 */
    public void grant(Player player, Quest quest) {
        for (QuestReward reward : quest.rewards()) {
            RewardType type = rewardTypes.find(reward.type()).orElse(null);
            if (type == null) {
                log(Level.WARNING, "任务 " + quest.id() + " 引用了未知奖励类型: " + reward.type());
                continue;
            }
            if (!type.available()) {
                log(Level.WARNING, "任务 " + quest.id() + " 的奖励类型 " + reward.type()
                        + " 当前不可用: " + type.unavailableReason());
                continue;
            }
            try {
                type.grant(player, reward);
            } catch (RuntimeException e) {
                // 单个奖励失败必须被隔离，否则玩家会连其它奖励一起拿不到
                log(Level.SEVERE, "发放奖励失败 任务=" + quest.id() + " 类型=" + reward.type() + " 原因=" + e.getMessage());
            }
        }
    }

    /** 校验任务的全部奖励类型是否可用，返回问题清单（编辑器与启动检查用）。 */
    public List<String> validate(Quest quest) {
        List<String> problems = new ArrayList<>();
        for (QuestReward reward : quest.rewards()) {
            if (reward.presetId() != null && reward.type().isBlank()) {
                // 引用的预设不存在：具体原因由 PresetRefs 报，这里不再补一句「未知奖励类型 」的空名字
                continue;
            }
            RewardType type = rewardTypes.find(reward.type()).orElse(null);
            if (type == null) {
                problems.add("未知奖励类型 " + reward.type());
            } else if (!type.available()) {
                problems.add("奖励类型 " + reward.type() + " 不可用（" + type.unavailableReason() + "）");
            }
        }
        return problems;
    }

    /** 玩家可领取的任务数量，用于 {@code %playertaskx_claimable%} 变量。 */
    public long claimableCount(UUID playerId) {
        return repository.findByPlayer(playerId).stream()
                .filter(playerQuest -> playerQuest.status() == QuestStatus.COMPLETED)
                .count();
    }

    /** 任务原始名（含颜色标签），交给 {@link ClaimOutcome} 在消息里一次性渲染。 */
    private static String rawName(Quest quest) {
        return quest.name() == null ? quest.id() : quest.name();
    }

    /**
     * 未满足的前置 → 给玩家看的名字。
     * <p>
     * 前置任务已被删除时退回 id：那种情况下「名字」本来就不存在，而 id 至少让管理员对得上。
     * 颜色标签一律剥掉，避免嵌进消息后把后半句也染上颜色。
     */
    private List<String> displayNames(List<String> missingIds) {
        List<String> names = new ArrayList<>(missingIds.size());
        for (String id : missingIds) {
            String raw = quests.find(id)
                    .map(quest -> quest.name() == null ? id : quest.name())
                    .orElse(id);
            names.add(TextRenderer.strip(raw));
        }
        return names;
    }

    private void log(Level level, String message) {
        Bukkit.getLogger().log(level, "[PlayerTaskX] " + message);
    }

    /**
     * 领取结果。
     * <p>
     * 为什么不让 {@code claim} 只回布尔值：玩家命令与 GUI 两个入口都要把「为什么领不到」
     * 还原成一句话，而两者各自去读状态、各自拼措辞的结果就是同一件事两种说法
     * （历史上这两处已经各写过一份）。把结论与措辞都放在这里，入口只负责调用。
     *
     * @param status    结果状态
     * @param questName 任务原始名（{@link #report} 用；任务不存在时为空串）
     * @param missing   未满足的前置显示名（仅 {@link Status#LOCKED} 非空）
     */
    public record ClaimOutcome(Status status, String questName, List<String> missing) {

        public enum Status {
            /** 已成功领取并写入账本 */
            CLAIMED,
            /** 该玩家没有这条任务记录 */
            NOT_ASSIGNED,
            /** 有记录但尚未完成 */
            NOT_COMPLETED,
            /** 已经领过 */
            ALREADY_CLAIMED,
            /** 已完成，但前置任务尚未全部领取 */
            LOCKED
        }

        public ClaimOutcome {
            missing = missing == null ? List.of() : List.copyOf(missing);
        }

        public boolean claimed() {
            return status == Status.CLAIMED;
        }

        /** 按状态把结果告诉玩家；措辞只在这里定义一次。 */
        public void report(MessageService messages, CommandSender receiver) {
            switch (status) {
                case CLAIMED -> messages.send(receiver, "quest.claimed", questName);
                case LOCKED -> messages.send(receiver, "quest.locked", String.join(", ", missing));
                case ALREADY_CLAIMED -> messages.send(receiver, "quest.already-claimed");
                case NOT_ASSIGNED -> messages.send(receiver, "quest.unavailable");
                case NOT_COMPLETED -> messages.send(receiver, "quest.not-completed");
            }
        }
    }
}
