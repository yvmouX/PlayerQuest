package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.Quest;

import java.util.List;
import java.util.Optional;

/**
 * 任务定义仓储：任务本体与其目标/奖励子树一起读写。
 * <p>
 * 写入采用「先删子树再重建」的策略——任务的目标/奖励是整体配置，
 * 逐条 diff 只会带来复杂度而没有收益。
 */
public interface QuestRepository {

    /** 读取全部任务（含目标与奖励）。 */
    List<Quest> findAll();

    /** 按 id 读取单个任务。 */
    Optional<Quest> findById(String id);

    /** 是否存在该任务。 */
    default boolean exists(String id) {
        return findById(id).isPresent();
    }

    /** 新增或覆盖任务（含目标与奖励）。 */
    void save(Quest quest);

    /** 批量保存，在单个事务内完成。 */
    void saveAll(List<Quest> quests);

    /** 删除任务及其目标/奖励。 */
    boolean delete(String id);

    /** 任务总数。 */
    long count();
}
