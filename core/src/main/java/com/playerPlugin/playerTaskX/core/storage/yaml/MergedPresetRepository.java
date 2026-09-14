package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.core.storage.DefinitionReadOnlyException;
import com.playerPlugin.playerTaskX.core.storage.PresetRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 预设的对外仓储：<b>数据库（可写） + presets/ 目录（只读，库优先）</b>。
 *
 * <p>与 {@link MergedQuestRepository} 同一套合并规则（见 {@link MergedSources}）：
 * 库里有同 id 的定义时库优先，文件里那份被忽略并告警；文件里的定义不允许改写。
 */
public final class MergedPresetRepository implements PresetRepository {

    private final PresetRepository database;
    private final MergedSources<Preset> merged;

    public MergedPresetRepository(PresetRepository database, YamlSources<Preset> files, Consumer<String> warner) {
        this.database = database;
        this.merged = new MergedSources<>(database, files, Preset::id, "预设", warner);
    }

    @Override
    public List<Preset> findAll() {
        return merged.all();
    }

    @Override
    public Optional<Preset> findById(String id) {
        return merged.find(id);
    }

    @Override
    public boolean isReadOnly(String id) {
        return merged.readOnly(id);
    }

    @Override
    public void save(Preset preset) {
        if (preset != null && merged.readOnly(preset.id())) {
            throw new DefinitionReadOnlyException(merged.readOnlyHint(preset.id()));
        }
        database.save(preset);
    }

    @Override
    public boolean delete(String id) {
        if (merged.readOnly(id)) {
            throw new DefinitionReadOnlyException(merged.readOnlyHint(id));
        }
        return database.delete(id);
    }

    @Override
    public long count() {
        return merged.count();
    }

}
