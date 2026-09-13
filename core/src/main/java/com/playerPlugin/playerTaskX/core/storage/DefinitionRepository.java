package com.playerPlugin.playerTaskX.core.storage;

import java.util.List;
import java.util.Optional;

/**
 * 「内容类」数据的仓储契约：任务定义、目标/奖励预设都归它管。
 *
 * <h2>为什么与玩家数据分成两个接口</h2>
 * 两者虽然都是"存储"，但操作形态不同，强行合并会让某一侧将就：
 * <ul>
 *   <li>内容类：整体载入、按 id 覆盖、极少写入，<b>不需要事务</b>；</li>
 *   <li>玩家数据：按（玩家, 任务）双键频繁读写、需要过滤查询与聚合、<b>需要事务</b>。</li>
 * </ul>
 * 合并的后果是二选一：要么玩家侧丢掉索引查询与事务（性能退化），
 * 要么内容侧被迫实现一个完整的键控可查询存储（等于拿内容表当数据库用）。
 * 因此拆成两个接口，但<b>由同一个 {@link DatabaseFactory} 装配</b>。
 *
 * <h2>id 是唯一身份</h2>
 * 实现不得用行号等外部信息推断 id：{@code id} 字段是权威来源。
 *
 * @param <T> 元素类型（{@code Quest} / {@code Preset}）
 */
public interface DefinitionRepository<T> {

    /** 载入全部元素。 */
    List<T> findAll();

    /** 按 id 取单个元素。 */
    Optional<T> findById(String id);

    /** 是否存在该 id。 */
    default boolean exists(String id) {
        return findById(id).isPresent();
    }

    /**
     * 新增或覆盖一个元素。
     * <p>
     * 实现必须保证本操作<b>要么完整生效、要么完全不生效</b>：任务与它的目标/奖励子表
     * 要落在同一个事务里，否则写一半崩溃会留下一个目标残缺、玩家进度却对得上的任务。
     */
    void save(T element);

    /** 删除元素；返回是否确实删掉了。 */
    boolean delete(String id);

    /** 元素总数。 */
    long count();
}
