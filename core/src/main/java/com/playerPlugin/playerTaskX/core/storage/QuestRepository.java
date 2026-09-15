package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.Quest;

/** 任务定义仓储：把 {@link DefinitionRepository} 绑到 {@link Quest} 上，唯一实现是 JDBC 那份（SQLite / MySQL 共用）。 */
public interface QuestRepository extends DefinitionRepository<Quest> {
}
