package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.QuestType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 玩家任务仓储：进行中的任务、每日发放状态、刷新次数。
 */
public interface PlayerQuestRepository {

    /** 某玩家的全部任务记录（含已完成/已领取）。 */
    List<PlayerQuest> findByPlayer(UUID playerId);

    /** 某玩家的进行中任务。 */
    List<PlayerQuest> findActiveByPlayer(UUID playerId);

    /** 按玩家与任务取单条记录。 */
    Optional<PlayerQuest> find(UUID playerId, String questId);

    /** 是否存在记录（任意状态）。 */
    default boolean exists(UUID playerId, String questId) {
        return find(playerId, questId).isPresent();
    }

    /** 新增或覆盖一条玩家任务记录。 */
    void save(PlayerQuest playerQuest);

    /** 在事务内批量保存。 */
    void saveAll(List<PlayerQuest> playerQuests);

    /**
     * 在单个事务内执行一组操作。
     * <p>
     * 之所以放在仓储接口上：每日任务刷新需要「删旧 + 写新 + 记状态」原子完成，
     * 否则中途失败会留下空的任务列表。让调用方直接依赖 {@code Database} 会把
     * 存储细节泄漏到业务层，因此在仓储层暴露这一个入口。
     */
    void transaction(Runnable work);

    /** 删除某玩家的某条任务记录（刷新每日任务时用）。 */
    void delete(UUID playerId, String questId);

    /** 删除某玩家指定类型的全部任务记录。 */
    void deleteByPlayerAndType(UUID playerId, QuestType type);

    /** 玩家总数（有任务记录的玩家数）。 */
    long countPlayers();

    /**
     * 有任务记录的全部玩家 id。
     * <p>
     * 供网页编辑器的管理端列出「哪些玩家有任务数据」，
     * 避免为了遍历而去枚举全服离线玩家。
     */
    java.util.List<UUID> distinctPlayerIds();
}
