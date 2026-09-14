package com.playerPlugin.playerTaskX.api.registry;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 任务定义注册表：内存中的任务视图，数据来自存储层。
 */
public interface QuestRegistry {

    Optional<Quest> find(String id);

    default boolean contains(String id) {
        return find(id).isPresent();
    }

    Collection<Quest> all();

    /** 已启用的任务。 */
    List<Quest> enabled();

    /**
     * 某种类型里已启用的任务。
     * <p>
     * 周期任务（每日/每周/每月/自定义）与普通任务共用这一个入口：多了类型之后，
     * 每个调用点各写一个 {@code filter(quest -> quest.type() == X)} 迟早会漏掉 enabled 判断。
     */
    default List<Quest> ofType(QuestType type) {
        return enabled().stream().filter(quest -> quest.type() == type).toList();
    }

    /** 所有周期任务（四种周期合起来），供编辑器与统计使用。 */
    default List<Quest> periodic() {
        return enabled().stream().filter(quest -> quest.type().isPeriodic()).toList();
    }

    /** 按分类筛选，分类为空表示不筛选。 */
    List<Quest> byCategory(String category);

    /** 所有分类名，用于 GUI 与编辑器。 */
    List<String> categories();

    /** 用最新数据替换全部任务定义（重载后调用）。 */
    void replaceAll(Collection<Quest> quests);

    /** 新增或更新单个任务，返回是否成功。 */
    boolean upsert(Quest quest);

    /** 删除任务，返回是否存在过。 */
    boolean remove(String id);
}
