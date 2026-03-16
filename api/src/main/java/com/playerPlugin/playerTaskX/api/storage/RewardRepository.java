package com.playerPlugin.playerTaskX.api.storage;

import com.playerPlugin.playerTaskX.api.model.RewardDefinition;

import java.util.List;
import java.util.Optional;

public interface RewardRepository {
    List<RewardDefinition> loadAll();
    Optional<RewardDefinition> findById(String id);
    void save(RewardDefinition def);
    void delete(String id);
}