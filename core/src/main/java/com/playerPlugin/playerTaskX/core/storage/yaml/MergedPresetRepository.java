package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.core.storage.PresetRepository;

import java.util.function.Consumer;

/**
 * 预设的对外仓储：<b>数据库（可写） + presets/ 目录（只读，库优先）</b>。
 *
 * <p>与 {@link MergedQuestRepository} 是同一套规则（{@link MergedDefinitionRepository}），
 * 这里只把类型绑到 {@link Preset} 上。
 */
public final class MergedPresetRepository extends MergedDefinitionRepository<Preset> implements PresetRepository {

    public MergedPresetRepository(PresetRepository database, YamlSources<Preset> files, Consumer<String> warner) {
        super(database, files, Preset::id, "预设", warner);
    }
}
