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

    // ------------------------------------------------------------------
    // 每日任务状态
    // ------------------------------------------------------------------

    /**
     * 每日任务状态：当前周期、已刷新次数、上次发放时刻。
     * <p>
     * 定义在接口上而不是某个实现里：它是玩家数据的一部分，
     * 所有后端都必须能存——否则换后端会静默丢掉刷新次数与周期判定。
     *
     * @param period       当前周期标识（如 {@code 2026-09-13}）
     * @param refreshCount 本周期已刷新次数，用于刷新上限
     * @param assignedAt   上次发放时刻的毫秒时间戳
     */
    record DailyState(String period, int refreshCount, long assignedAt) {
    }

    /** 读取每日状态；从未发放过时返回 null。 */
    DailyState findDailyState(UUID playerId);

    /** 写入每日状态。 */
    void saveDailyState(UUID playerId, String period, int refreshCount, long assignedAt);

    /**
     * 清除每日状态。
     * <p>
     * 提供默认空实现：并非所有后端都需要它（例如只用内存替身的测试），
     * 而 {@link #findDailyState} 与 {@link #saveDailyState} 没有默认实现——
     * 它们是功能主体，缺失必须让编译器报错，而不是静默丢掉刷新次数。
     */
    default void deleteDailyState(UUID playerId) {
        // 由具备该能力的后端覆盖
    }
}
