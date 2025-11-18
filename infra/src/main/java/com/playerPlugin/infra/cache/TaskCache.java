package com.playerPlugin.infra.cache;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.playerPlugin.core.domain.PlayerTask.Task.TaskDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 在 `TaskService.reload()` 时用 `TaskRepository.loadAll()` 更新缓存；在 `UpdateProgressUseCase` 中优先从缓存读取任务定义。
 */
public class TaskCache {
    private final ConcurrentMap<String, TaskDefinition> byId = new ConcurrentHashMap<>();
    private final Multimap<String, TaskDefinition> byType = Multimaps.synchronizedSetMultimap(HashMultimap.create());

    public void reload(Collection<TaskDefinition> tasks) {
        byId.clear();
        byType.clear();
        for (TaskDefinition t : tasks) {
            byId.put(t.getId(), t);
            byType.put(t.getType(), t);
        }
    }

    public Optional<TaskDefinition> getById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<TaskDefinition> getByType(String type) {
        return new ArrayList<>(byType.get(type));
    }
}
