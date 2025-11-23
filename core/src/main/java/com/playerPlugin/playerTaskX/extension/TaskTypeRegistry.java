package com.playerPlugin.playerTaskX.extension;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TaskTypeRegistry {
    private final ConcurrentMap<String, TaskType> registry = new ConcurrentHashMap<>();

    public void register(TaskType t) {
        Objects.requireNonNull(t);
        if (registry.putIfAbsent(t.id(), t) != null) {
            throw new IllegalStateException("TaskType already registered: " + t.id());
        }
    }

    public Optional<TaskType> get(String id) {
        return Optional.ofNullable(registry.get(id));
    }

    public Collection<TaskType> all() {
        return Collections.unmodifiableCollection(registry.values());
    }
}
