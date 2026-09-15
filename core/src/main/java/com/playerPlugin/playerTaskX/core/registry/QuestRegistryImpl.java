package com.playerPlugin.playerTaskX.core.registry;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.registry.QuestRegistry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内存中的任务注册表：引擎与 GUI 的一切任务查询都走这里，存储只是它的持久化备份。
 * 遍历顺序会体现在界面列表上，因此保持插入顺序。
 */
public final class QuestRegistryImpl implements QuestRegistry {

    /** 保持插入顺序：注册表的遍历顺序会体现在界面列表上，随加载顺序变化会让人以为数据变了。 */
    private final Map<String, Quest> quests = new LinkedHashMap<>();

    @Override
    public Optional<Quest> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(quests.get(id));
    }

    @Override
    public Collection<Quest> all() {
        return List.copyOf(quests.values());
    }

    @Override
    public List<Quest> enabled() {
        return quests.values().stream().filter(Quest::enabled).toList();
    }

    @Override
    public void replaceAll(Collection<Quest> replacing) {
        quests.clear();
        if (replacing != null) {
            for (Quest quest : replacing) {
                upsert(quest);
            }
        }
    }

    @Override
    public boolean upsert(Quest quest) {
        if (quest == null || quest.id() == null || quest.id().isBlank()) {
            return false;
        }
        quests.put(quest.id(), quest);
        return true;
    }

    @Override
    public boolean remove(String id) {
        return id != null && quests.remove(id) != null;
    }
}
