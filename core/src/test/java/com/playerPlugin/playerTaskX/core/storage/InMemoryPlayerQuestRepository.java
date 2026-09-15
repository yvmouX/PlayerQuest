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
 * 玩家任务仓储的内存实现，供引擎侧、奖励侧测试使用；放在 {@code core.storage} 而不是某个测试类里，是因为进度引擎、奖励发放、每日抽取三处都要用它，各写一份迟早会让其中一份悄悄走偏。
 * 它刻意不模拟「中途失败」：事务回滚由 {@code StorageIntegrationTest} 在真实 SQLite 上验证，内存里假装回滚只会给人「已经测过了」的错觉。
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
    public PeriodState findPeriodState(UUID playerId, QuestType type) {
        return null;
    }

    @Override
    public void savePeriodState(UUID playerId, QuestType type, String period, int refreshCount, long assignedAt) {
        // 测试替身不持久化周期状态；需要它的测试用这个字段自己接管
        periodStates.put(playerId + "/" + type, new PeriodState(period, refreshCount, assignedAt));
    }

    /** 记录写入过的周期状态，供测试断言「刷新次数有没有写回」。 */
    public final Map<String, PeriodState> periodStates = new LinkedHashMap<>();
}
