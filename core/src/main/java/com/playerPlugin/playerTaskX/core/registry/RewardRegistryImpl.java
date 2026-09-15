package com.playerPlugin.playerTaskX.core.registry;

import com.playerPlugin.playerTaskX.api.registry.RewardRegistry;
import com.playerPlugin.playerTaskX.api.reward.RewardType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 奖励类型注册表实现：只负责「按 id 找类型」，可用性判断在 {@link RewardType#available()}，任务级校验在 {@code QuestAdminService.validate}。 */
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
}
