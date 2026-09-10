package com.playerPlugin.playerTaskX.core.quest;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.registry.QuestRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内存中的任务注册表。
 * <p>
 * 单一数据源原则：所有任务查询都走这里，磁盘只是它的持久化备份，
 * 因此引擎与 GUI 不需要感知存储实现。
 */
public final class QuestRegistryImpl implements QuestRegistry {

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
    public List<Quest> byCategory(String category) {
        if (category == null || category.isBlank()) {
            return enabled();
        }
        return enabled().stream()
                .filter(quest -> category.equalsIgnoreCase(quest.category()))
                .toList();
    }

    @Override
    public List<String> categories() {
        return enabled().stream()
                .map(Quest::category)
                .filter(category -> category != null && !category.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public void replaceAll(Collection<Quest> replacing) {
        quests.clear();
        if (replacing != null) {
            for (Quest quest : replacing) {
                if (quest != null && quest.id() != null && !quest.id().isBlank()) {
                    quests.put(quest.id(), quest);
                }
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

    /** 按分类分组，供 GUI 与编辑器直接使用。 */
    public Map<String, List<Quest>> groupedByCategory() {
        Map<String, List<Quest>> grouped = new LinkedHashMap<>();
        List<Quest> sorted = new ArrayList<>(enabled());
        sorted.sort(Comparator.comparing(Quest::id));
        for (Quest quest : sorted) {
            String category = (quest.category() == null || quest.category().isBlank()) ? "未分类" : quest.category();
            grouped.computeIfAbsent(category, key -> new ArrayList<>()).add(quest);
        }
        return grouped;
    }
}
