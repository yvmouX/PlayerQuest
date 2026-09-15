package com.playerPlugin.playerTaskX.api.registry;

import com.playerPlugin.playerTaskX.api.reward.RewardType;

import java.util.Collection;
import java.util.Optional;

/** 奖励类型注册表。 */
public interface RewardRegistry {

    /** 注册一个奖励类型；重复 id 会被拒绝并告警。 */
    void register(RewardType type);

    Optional<RewardType> find(String id);

    default boolean contains(String id) {
        return find(id).isPresent();
    }

    Collection<RewardType> all();

    default String displayName(String id) {
        return find(id).map(RewardType::displayName).orElse(id);
    }
}
