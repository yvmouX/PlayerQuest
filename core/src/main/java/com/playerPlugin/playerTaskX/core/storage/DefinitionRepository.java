package com.playerPlugin.playerTaskX.core.storage;

import java.util.List;
import java.util.Optional;

/**
 * 「内容类」数据的仓储契约：任务定义与目标/奖励预设，整体载入、按 id 覆盖、极少写入，因而无需事务。
 * id 是唯一身份（不得用行号推断）；{@link #save} 必须原子，任务与它的目标/奖励子表要落在同一个事务里。
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

    /**
     * 该 id 的定义是否为<b>只读</b>（来自 YAML 文件而不是数据库）。
     * <p>
     * 只有「库 + 文件」合并的那层实现会返回 true：文件定义不进数据库，因此游戏内命令与
     * GUI 都不能改它们。默认 false 让纯数据库实现无需关心这件事。
     */
    default boolean isReadOnly(String id) {
        return false;
    }
}
