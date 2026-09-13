package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.Preset;

import java.util.List;

/**
 * 预设仓储：目标/奖励预设的存取。
 *
 * <p>与 {@link QuestRepository} 共用 {@link DefinitionRepository} 契约——两者都是
 * 「内容类数据」（整体载入、按 id 覆盖、极少写入、不需要事务），只是元素类型不同。
 * 唯一实现是 {@link com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcPresetRepository}。
 *
 * <p>预设只有网页编辑器使用，游戏引擎完全不读它。
 * 类别（目标/奖励）记录在 {@link Preset#kind()} 上，因此不需要按类别拆成两个仓储。
 */
public interface PresetRepository extends DefinitionRepository<Preset> {

    /**
     * 库为空时写入出厂默认预设；非空时什么都不做。
     * <p>
     * 放在接口上而不是某个实现里：默认预设是「首次启动的样例数据」，与存储后端无关，
     * 装配点不该为了调它去认具体实现。
     */
    void seedIfEmpty(List<Preset> defaults);
}
