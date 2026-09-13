package com.playerPlugin.playerTaskX.core.registry;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.registry.ObjectiveRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 目标类型注册表实现。
 * <p>
 * 除按 id 查找外，还维护「动作类型 → 目标类型」的分发索引：
 * 每次游戏动作都要判定，这一步决定了热路径的开销。
 */
public final class ObjectiveRegistryImpl implements ObjectiveRegistry {

    private final Map<String, ObjectiveType> byId = new LinkedHashMap<>();
    private final Map<Trigger, List<ObjectiveType>> byTrigger = new EnumMap<>(Trigger.class);

    /** 注册失败的类型 id → 原因，供启动日志汇总。 */
    private final Map<String, String> rejected = new LinkedHashMap<>();

    @Override
    public void register(ObjectiveType type) {
        if (type == null || type.id() == null || type.id().isBlank()) {
            return;
        }
        String id = type.id().toLowerCase(Locale.ROOT);
        if (byId.containsKey(id)) {
            rejected.put(id, "id 重复");
            return;
        }
        byId.put(id, type);
        byTrigger.computeIfAbsent(type.trigger(), key -> new ArrayList<>()).add(type);
    }

    @Override
    public Optional<ObjectiveType> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Collection<ObjectiveType> all() {
        return List.copyOf(byId.values());
    }

    @Override
    public List<ObjectiveType> byTrigger(Trigger trigger) {
        return byTrigger.getOrDefault(trigger, List.of());
    }

    /** 注册被拒绝的类型及原因。 */
    public Map<String, String> rejected() {
        return Map.copyOf(rejected);
    }
}
