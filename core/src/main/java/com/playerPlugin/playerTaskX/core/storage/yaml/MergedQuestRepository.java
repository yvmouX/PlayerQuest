package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;

import java.util.function.Consumer;

/**
 * 任务定义的对外仓储：数据库（可写） + {@code quests/} 目录（只读，库优先），规则见 {@link MergedDefinitionRepository}。
 * 额外提供 {@link #isReadOnly(String)}，命令与 GUI 据此把文件里的定义标成只读。
 */
public final class MergedQuestRepository extends MergedDefinitionRepository<Quest> implements QuestRepository {

    public MergedQuestRepository(QuestRepository database, YamlSources<Quest> files, Consumer<String> warner) {
        super(database, files, Quest::id, "任务", warner);
    }
}
