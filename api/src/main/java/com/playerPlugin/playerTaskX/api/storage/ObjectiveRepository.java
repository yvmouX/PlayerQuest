package com.playerPlugin.playerTaskX.api.storage;

import com.playerPlugin.playerTaskX.api.model.ObjectiveDefinition;

import java.util.List;
import java.util.Optional;

public interface ObjectiveRepository {
    List<ObjectiveDefinition> loadAll();
    Optional<ObjectiveDefinition> findById(String id);
    void save(ObjectiveDefinition def);
    void delete(String id);
}