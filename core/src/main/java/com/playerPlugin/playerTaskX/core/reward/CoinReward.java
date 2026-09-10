package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * 奖励：任务币（插件内建的虚拟货币）。
 * <p>
 * 为什么自建而不是复用 Vault：任务币是「任务系统专属货币」，
 * 常见用途是让玩家攒起来在任务商店消费。若映射到金币，
 * 就与服务器经济完全重合，失去了独立计价的意义；
 * 存插件自己的表也意味着没有 Vault 的服务器同样可用。
 * <p>
 * 余额存于 {@code player_coin} 表，读写都经仓储，因此 MySQL/SQLite 行为一致。
 */
public final class CoinReward implements RewardType {

    public static final String ID = "quest_coin";

    private final PlayerQuestRepository repository;

    public CoinReward(PlayerQuestRepository repository) {
        this.repository = repository;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "任务币";
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.integer("amount", "数量", 10, "发放的任务币数量")
        );
    }

    @Override
    public void grant(Player player, QuestReward reward) {
        int amount = reward.integer("amount", 0);
        if (amount <= 0) {
            return;
        }
        repository.addCoin(player.getUniqueId(), amount);
    }

    /** 余额查询（GUI、变量与命令使用）。 */
    public long balance(UUID playerId) {
        return repository.coinBalance(playerId);
    }

    /**
     * 扣除任务币，余额不足时返回 false 且不做任何改动。
     * <p>
     * 刷新每日任务的扣费走这里，因此与金币/点券是同一套语义。
     */
    public boolean take(UUID playerId, long amount) {
        if (amount <= 0) {
            return true;
        }
        return repository.addCoin(playerId, -amount);
    }
}
