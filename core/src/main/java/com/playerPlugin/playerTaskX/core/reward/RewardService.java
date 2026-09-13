package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.registry.QuestRegistry;
import com.playerPlugin.playerTaskX.api.registry.RewardRegistry;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import org.bukkit.Bukkit;
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
     * @return 是否领取成功（false 表示任务未完成、已被领取或不存在）
     */
    public boolean claim(Player player, String questId) {
        Quest quest = quests.find(questId).orElse(null);
        if (quest == null) {
            return false;
        }
        PlayerQuest playerQuest = repository.find(player.getUniqueId(), questId).orElse(null);
        if (playerQuest == null || playerQuest.status() != QuestStatus.COMPLETED) {
            // 只有「已完成待领取」才能领；重复领取会被这里挡住
            return false;
        }

        grant(player, quest);

        playerQuest.status(QuestStatus.CLAIMED);
        repository.save(playerQuest);
        return true;
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

    private void log(Level level, String message) {
        Bukkit.getLogger().log(level, "[PlayerTaskX] " + message);
    }
}
