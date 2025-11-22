package com.playerPlugin.infra.cache;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.playerPlugin.core.domain.Task.TaskDefinition;
import com.playerPlugin.core.domain.Task.TaskProgress;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 在 `TaskService.reload()` 时用 `TaskRepository.loadAll()` 更新缓存；在 `UpdateProgressUseCase` 中优先从缓存读取任务定义。
 */
public class TaskCache {
    private Map<UUID, List<TaskProgress>> cache;

    private final Set<UUID> dirtyEntries = ConcurrentHashMap.newKeySet();
    private final Set<TaskProgress> maybeFinishedEntries = ConcurrentHashMap.newKeySet();

    private final ConcurrentMap<String, TaskDefinition> byId = new ConcurrentHashMap<>();
    private final Multimap<String, TaskDefinition> byType = Multimaps.synchronizedSetMultimap(HashMultimap.create());

    public TaskCache() {
        cache = Collections.synchronizedMap(
                new LinkedHashMap<UUID, List<TaskProgress>>(
                        100, // 初始容量 100个玩家 TODO 配置文件自定义
                        0.75f,
                        true
                ) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<UUID, List<TaskProgress>> eldest) {
                        // 当缓存超过最大容量时，移除最久未使用的条目
                        // 但如果是脏数据，先保存到数据库
                        if (size() > 100) {
                            if (getDirtyEntries().contains(eldest.getKey())) {
                                saveCacheToDatabase(eldest.getValue(), false);
                                getDirtyEntries().remove(eldest.getKey());
                            }
                            return true;
                        }
                        return false;
                    }
                });
    }

    public Map<UUID, List<TaskProgress>>  getCache() {
        return cache;
    }

    public Set<UUID> getDirtyEntries() {
        return dirtyEntries;
    }

    public Set<TaskProgress> getMaybeFinishedEntries() {
        return maybeFinishedEntries;
    }

    public Optional<TaskDefinition> getById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<TaskDefinition> getByType(String type) {
        return new ArrayList<>(byType.get(type));
    }

//    public void reload(Collection<TaskDefinition> tasks) {
//        byId.clear();
//        byType.clear();
//        for (TaskDefinition t : tasks) {
//            byId.put(t.getId(), t);
//            byType.put(t.getType(), t);
//        }
//    }
}