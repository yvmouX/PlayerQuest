package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.QuestType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 玩家任务仓储：进行中的任务、周期发放状态、刷新次数。 */
public interface PlayerQuestRepository {

    /** 某玩家的全部任务记录（含已完成/已领取）。 */
    List<PlayerQuest> findByPlayer(UUID playerId);

    /** 某玩家的进行中任务。 */
    List<PlayerQuest> findActiveByPlayer(UUID playerId);

    /** 按玩家与任务取单条记录。 */
    Optional<PlayerQuest> find(UUID playerId, String questId);

    /** 新增或覆盖一条玩家任务记录。 */
    void save(PlayerQuest playerQuest);

    /** 在单个事务内执行一组操作；放上契约是为了让「删旧 + 写新 + 记状态」原子完成，又不把 {@code Database} 泄漏到业务层。 */
    void transaction(Runnable work);

    /** 删除某玩家的某条任务记录（刷新每日任务时用）。 */
    void delete(UUID playerId, String questId);

    /** 删除某玩家指定类型的全部任务记录。 */
    void deleteByPlayerAndType(UUID playerId, QuestType type);

    // ------------------------------------------------------------------
    // 周期任务状态（每日 / 每周 / 每月 / 自定义各一条）
    // ------------------------------------------------------------------

    /**
     * 某种周期的发放状态；定义在接口上，所有后端都必须能存，否则换后端会静默丢掉刷新次数与周期判定。
     * @param period     周期标识（如 {@code 2026-09-13}、{@code W2026-09-14}、{@code C3d#6893}）
     * @param assignedAt 上次发放时刻的毫秒时间戳
     */
    record PeriodState(String period, int refreshCount, long assignedAt) {
    }

    /** 读取某种周期的状态；该周期从未发放过时返回 null。 */
    PeriodState findPeriodState(UUID playerId, QuestType type);

    /** 写入某种周期的状态。 */
    void savePeriodState(UUID playerId, QuestType type, String period, int refreshCount, long assignedAt);
}
