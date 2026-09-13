package com.playerPlugin.playerTaskX.core.storage;

import java.util.Set;
import java.util.UUID;

/**
 * 永久领取账本：某玩家是否领取过某任务的奖励。
 *
 * <h2>为什么必须与 {@link PlayerQuestRepository} 分开</h2>
 * 两者生命周期完全不同：{@code player_quest} 存的是<b>当前这批任务</b>，
 * 每日任务跨天/刷新时会被整批删掉（{@code DailyService.assign}）；而「前置任务是否已完成」
 * 必须跨天成立，否则任务链一到第二天就断。因此账本只记「领取」这一个事实，永不删除。
 *
 * <p>契约刻意做到最小：前置判定只需要「这名玩家领取过哪些任务 id」，
 * 因此既没有单条查询也没有时间维度——时间戳只是落库时顺手记下，避免将来要它时
 * 才发现历史数据没有。</p>
 *
 * <p>只有领取会写账本，不记「已完成但未领取」：前者的判定标准就是已领奖，
 * 多存一种状态等于让「什么算完成」有两个答案。</p>
 */
public interface QuestClaimRepository {

    /** 该玩家已领取奖励的全部任务 id；没有记录时返回空集合。 */
    Set<String> claimedQuestIds(UUID playerId);

    /**
     * 记下一条领取事实（已存在则覆盖时间戳）。
     * <p>
     * 幂等：重复领取本就会被状态机挡住，这里再挡一次没有意义，覆盖即可。
     */
    void markClaimed(UUID playerId, String questId, long claimedAt);
}
