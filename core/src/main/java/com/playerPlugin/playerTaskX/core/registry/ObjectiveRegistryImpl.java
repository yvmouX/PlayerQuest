package com.playerPlugin.playerTaskX.core.registry;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.registry.ObjectiveRegistry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 目标类型注册表实现：只维护「id → 类型」的映射。
 * 不做「动作类型 → 目标类型」的分发索引——注册表是全局的，而热路径要按玩家已接任务过滤，缓存到注册表里反而用不上。
 */
public final class ObjectiveRegistryImpl implements ObjectiveRegistry {

    private final Map<String, ObjectiveType> byId = new LinkedHashMap<>();

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
    }

    @Override
    public Optional<ObjectiveType> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Collection<ObjectiveType> all() {
        return List.copyOf(byId.values());
    }

    /** 注册被拒绝的类型及原因。 */
    public Map<String, String> rejected() {
        return Map.copyOf(rejected);
    }
}
