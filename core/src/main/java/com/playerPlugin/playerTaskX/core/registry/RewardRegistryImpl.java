package com.playerPlugin.playerTaskX.core.registry;

import com.playerPlugin.playerTaskX.api.registry.RewardRegistry;
import com.playerPlugin.playerTaskX.api.reward.RewardType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 奖励类型注册表实现。
 */
public final class RewardRegistryImpl implements RewardRegistry {

    private final Map<String, RewardType> byId = new LinkedHashMap<>();

    @Override
    public void register(RewardType type) {
        if (type == null || type.id() == null || type.id().isBlank()) {
            return;
        }
        byId.put(type.id().toLowerCase(Locale.ROOT), type);
    }

    @Override
    public Optional<RewardType> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Collection<RewardType> all() {
        return List.copyOf(byId.values());
    }

    @Override
    public List<RewardType> available() {
        return byId.values().stream().filter(RewardType::available).toList();
    }

    @Override
    public List<String> validate(Collection<String> usedTypeIds) {
        List<String> problems = new ArrayList<>();
        for (String id : usedTypeIds) {
            RewardType type = find(id).orElse(null);
            if (type == null) {
                problems.add("未知奖励类型: " + id);
            } else if (!type.available()) {
                problems.add("奖励类型 " + id + " 当前不可用: " + type.unavailableReason());
            }
        }
        return problems;
    }
}
