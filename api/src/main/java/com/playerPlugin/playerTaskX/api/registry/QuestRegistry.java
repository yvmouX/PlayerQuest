package com.playerPlugin.playerTaskX.api.registry;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestType;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 任务定义注册表：引擎、GUI 与命令都从这里查任务，存储只是它的持久化备份。 */
public interface QuestRegistry {

    Optional<Quest> find(String id);

    default boolean contains(String id) {
        return find(id).isPresent();
    }

    /**
     * 全部任务，<b>按 id 升序</b>。
     * <p>
     * 顺序由注册表担保（而不是存储读取顺序）：翻页界面与命令列表都要「同一份数据每次得到同样的顺序」，
     * 否则切换一次启用状态刷新后，任务会跳到别的页。要别的顺序，用 {@link #all(Comparator)}。
     */
    List<Quest> all();

    /**
     * 全部任务，按给定顺序排（{@code order} 为 {@code null} 时等同 {@link #all()}）。
     * <p>
     * 排序稳定：比较结果相同的任务（例如同名）保持 id 升序，因此翻页不会因为「同分」而抖。
     */
    default List<Quest> all(Comparator<Quest> order) {
        if (order == null) {
            return all();
        }
        return all().stream().sorted(order).toList();
    }

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

    /** 用最新数据替换全部任务定义（重载后调用）。 */
    void replaceAll(Collection<Quest> quests);

    /** 新增或更新单个任务，返回是否成功。 */
    boolean upsert(Quest quest);

    /** 删除任务，返回是否存在过。 */
    boolean remove(String id);
}
