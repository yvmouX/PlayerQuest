package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.Quest;

/**
 * 任务定义仓储。
 *
 * <p>本身不新增方法，只是把 {@link DefinitionRepository} 绑到 {@link Quest} 上，
 * 并给「任务定义」一个可读的类型名。后端实现：
 * {@link QuestFileRepository}（JSON 文件，默认）、
 * {@link com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcQuestRepository}（SQLite / MySQL）。
 *
 * <p>为什么任务定义与玩家数据分成两个接口，见 {@link DefinitionRepository} 的类注释。
 */
public interface QuestRepository extends DefinitionRepository<Quest> {
}
