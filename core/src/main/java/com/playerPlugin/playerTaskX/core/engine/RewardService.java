package com.playerPlugin.playerTaskX.core.engine;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.registry.QuestRegistry;
import com.playerPlugin.playerTaskX.api.registry.RewardRegistry;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import cn.yvmou.ylib.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 奖励发放：领取校验、逐个发放、状态落库。
 * 单个奖励发放失败不影响其它奖励；「未接取 / 未完成 / 已领过」的判定与措辞只有这一处，命令、GUI 与占位符不可能有两种说法。
 */
public final class RewardService {

    private final QuestRegistry quests;
    private final RewardRegistry rewardTypes;
    private final PlayerQuestRepository repository;

    public RewardService(QuestRegistry quests, RewardRegistry rewardTypes, PlayerQuestRepository repository) {
        this.quests = quests;
        this.rewardTypes = rewardTypes;
        this.repository = repository;
    }

    /**
     * 领取任务奖励。
     *
     * @return 领取结果；失败原因由 {@link ClaimOutcome.Status} 给出，调用方不必自己猜
     */
    public ClaimOutcome claim(Player player, String questId) {
        Quest quest = quests.find(questId).orElse(null);
        if (quest == null) {
            return new ClaimOutcome(ClaimOutcome.Status.NOT_ASSIGNED, "");
        }
        PlayerQuest playerQuest = repository.find(player.getUniqueId(), questId).orElse(null);
        if (playerQuest == null) {
            return new ClaimOutcome(ClaimOutcome.Status.NOT_ASSIGNED, rawName(quest));
        }
        if (playerQuest.status() == QuestStatus.CLAIMED) {
            // 重复领取会被这里挡住
            return new ClaimOutcome(ClaimOutcome.Status.ALREADY_CLAIMED, rawName(quest));
        }
        if (playerQuest.status() != QuestStatus.COMPLETED) {
            return new ClaimOutcome(ClaimOutcome.Status.NOT_COMPLETED, rawName(quest));
        }
        grant(player, quest);

        playerQuest.status(QuestStatus.CLAIMED);
        repository.save(playerQuest);
        return new ClaimOutcome(ClaimOutcome.Status.CLAIMED, rawName(quest));
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

    /** 校验任务的全部奖励类型是否可用，返回问题清单（配置校验与启动检查用）。 */
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


    private void log(Level level, String message) {
        Bukkit.getLogger().log(level, "[PlayerTaskX] " + message);
    }

    /** 领取结果：结论与措辞都放在这里，命令与 GUI 只负责调用，避免同一件事两种说法。 */
    public record ClaimOutcome(Status status, String questName) {

        public enum Status {
            /** 已成功领取 */
            CLAIMED,
            /** 该玩家没有这条任务记录 */
            NOT_ASSIGNED,
            /** 有记录但尚未完成 */
            NOT_COMPLETED,
            /** 已经领过 */
            ALREADY_CLAIMED
        }

        public boolean claimed() {
            return status == Status.CLAIMED;
        }

        /** 按状态把结果告诉玩家；措辞只在这里定义一次。 */
        public void report(MessageService messages, CommandSender receiver) {
            switch (status) {
                case CLAIMED -> messages.send(receiver, "quest.claimed", questName);
                case ALREADY_CLAIMED -> messages.send(receiver, "quest.already-claimed");
                case NOT_ASSIGNED -> messages.send(receiver, "quest.unavailable");
                case NOT_COMPLETED -> messages.send(receiver, "quest.not-completed");
            }
        }
    }
}
