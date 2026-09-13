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
 * <p>与 {@link MergedQuestRepository} 同一套规则，只是多一个
 * {@link #seedIfEmpty(List)}：出厂默认预设写库时也要看「插件实际能用多少预设」，
 * 文件里已经有预设时不再灌入默认值。
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

    @Override
    public void seedIfEmpty(List<Preset> defaults) {
        if (count() > 0) {
            return;
        }
        database.seedIfEmpty(defaults);
    }
}
