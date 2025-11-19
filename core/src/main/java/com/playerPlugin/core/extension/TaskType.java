package com.playerPlugin.core.extension;

import com.playerPlugin.core.domain.Task.TaskCondition;

public interface TaskType {
    String id(); // unique id, e.g. "kill"

    // 当 core 收到 platform 事件并转换为 CoreEvent（如 CoreKillEvent）时，TaskType 判断该事件是否匹配条件
    boolean matches(TaskCondition cond, Object coreEvent);

    // 从 coreEvent 中抽取计数（例如一次击杀算 1）
    int extractCount(TaskCondition cond, Object coreEvent);
}
