package com.playerPlugin.playerTaskX.core.storage;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 内存领取账本，供不碰数据库的测试使用。
 *
 * <p>放在这里而不是各个测试类里各写一份：前置判定、领取门禁、每日抽取三处测试都要它，
 * 抄三份的下场是其中一份悄悄改了语义（例如忘了按玩家隔离），而测试照样绿。
 */
public final class InMemoryQuestClaimRepository implements QuestClaimRepository {

    private final Map<UUID, Set<String>> claimedByPlayer = new HashMap<>();

    @Override
    public Set<String> claimedQuestIds(UUID playerId) {
        if (playerId == null) {
            return Set.of();
        }
        Set<String> claimed = claimedByPlayer.get(playerId);
        return claimed == null ? Set.of() : Set.copyOf(claimed);
    }

    @Override
    public void markClaimed(UUID playerId, String questId, long claimedAt) {
        if (playerId == null || questId == null) {
            return;
        }
        claimedByPlayer.computeIfAbsent(playerId, key -> new LinkedHashSet<>()).add(questId);
    }

    /** 预置一条领取记录：用于构造「前置已满足」的场景，不经过领取流程。 */
    public void given(UUID playerId, String questId) {
        markClaimed(playerId, questId, 0L);
    }
}
