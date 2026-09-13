package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.QuestType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 玩家任务仓储的内存实现，供引擎侧、奖励侧测试使用。
 *
 * <p>为什么不模拟「中途失败」：那不是它的职责。事务回滚的真实行为由
 * {@code StorageIntegrationTest} 在真实 SQLite 上验证——内存实现里假装回滚
 * 只会给人「已经测过了」的错觉。</p>
 *
 * <p>放在 {@code core.storage} 而不是某个测试类里：进度引擎、奖励发放、每日抽取
 * 三处都要用它，各写一份的下场是其中一份的行为悄悄与另外两份不同。</p>
 */
public final class InMemoryPlayerQuestRepository implements PlayerQuestRepository {

    private final Map<UUID, Map<String, PlayerQuest>> data = new HashMap<>();

    @Override
    public List<PlayerQuest> findByPlayer(UUID playerId) {
        return new ArrayList<>(data.getOrDefault(playerId, Map.of()).values());
    }

    @Override
    public List<PlayerQuest> findActiveByPlayer(UUID playerId) {
        return findByPlayer(playerId).stream().filter(PlayerQuest::isActive).toList();
    }

    @Override
    public Optional<PlayerQuest> find(UUID playerId, String questId) {
        return Optional.ofNullable(data.getOrDefault(playerId, Map.of()).get(questId));
    }

    @Override
    public void save(PlayerQuest playerQuest) {
        data.computeIfAbsent(playerQuest.playerId(), key -> new LinkedHashMap<>())
                .put(playerQuest.questId(), playerQuest);
    }

    @Override
    public void transaction(Runnable work) {
        work.run();
    }

    @Override
    public void delete(UUID playerId, String questId) {
        Map<String, PlayerQuest> perPlayer = data.get(playerId);
        if (perPlayer != null) {
            perPlayer.remove(questId);
        }
    }

    @Override
    public void deleteByPlayerAndType(UUID playerId, QuestType type) {
        Map<String, PlayerQuest> perPlayer = data.get(playerId);
        if (perPlayer != null) {
            perPlayer.values().removeIf(playerQuest -> playerQuest.type() == type);
        }
    }

    @Override
    public long countPlayers() {
        return data.size();
    }

    @Override
    public List<UUID> distinctPlayerIds() {
        return new ArrayList<>(data.keySet());
    }

    @Override
    public DailyState findDailyState(UUID playerId) {
        return null;
    }

    @Override
    public void saveDailyState(UUID playerId, String period, int refreshCount, long assignedAt) {
        // 测试替身不持久化每日状态
    }
}
