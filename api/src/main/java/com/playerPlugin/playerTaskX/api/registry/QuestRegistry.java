package com.playerPlugin.playerTaskX.api.registry;

import com.playerPlugin.playerTaskX.api.model.Quest;

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

    /** 每日任务池（已启用）。 */
    default List<Quest> daily() {
        return enabled().stream().filter(Quest::isDaily).toList();
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
