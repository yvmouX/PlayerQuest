package com.playerPlugin.playerTaskX.core.engine;

import java.util.List;
import java.util.UUID;

/** 一次动作处理的结果（改没改进度、刚完成哪些任务），引擎只报「改了什么」，「怎么告诉玩家」由调用方决定。 */
public record ApplyResult(UUID playerId, boolean changed, List<String> completedQuests) {

    public static final ApplyResult NONE = new ApplyResult(null, false, List.of());
}
