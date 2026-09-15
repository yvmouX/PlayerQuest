package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.core.storage.PresetRepository;

import java.util.function.Consumer;

/** 预设的对外仓储：数据库（可写） + {@code presets/} 目录（只读，库优先），规则见 {@link MergedDefinitionRepository}。 */
public final class MergedPresetRepository extends MergedDefinitionRepository<Preset> implements PresetRepository {

    public MergedPresetRepository(PresetRepository database, YamlSources<Preset> files, Consumer<String> warner) {
        super(database, files, Preset::id, "预设", warner);
    }
}
