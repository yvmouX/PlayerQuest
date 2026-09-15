package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;

import java.util.function.Consumer;

/**
 * 任务定义的对外仓储：<b>数据库（可写） + quests/ 目录（只读，库优先）</b>。
 *
 * <p>装配点用这一份替换纯数据库实现，于是「任务从哪来」对上层完全透明：引擎、GUI、
 * 编辑器拿到的都是同一份合并结果；唯一多出来的信息是 {@link #isReadOnly(String)}，
 * 编辑器据此把文件里的定义标成只读。
 *
 * <p>规则本身在 {@link MergedDefinitionRepository}，这里只把类型绑到 {@link Quest} 上。
 */
public final class MergedQuestRepository extends MergedDefinitionRepository<Quest> implements QuestRepository {

    public MergedQuestRepository(QuestRepository database, YamlSources<Quest> files, Consumer<String> warner) {
        super(database, files, Quest::id, "任务", warner);
    }
}
