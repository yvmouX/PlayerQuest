package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.Preset;

/**
 * 预设仓储（与 {@link QuestRepository} 共用 {@link DefinitionRepository} 契约，只是元素类型不同）。
 * 预设不直接参与判定：任务里存的只是引用，由 {@code PresetRefs} 在 reload/save 时展开进任务定义。
 */
public interface PresetRepository extends DefinitionRepository<Preset> {
}
