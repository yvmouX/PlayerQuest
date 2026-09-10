package com.playerPlugin.playerTaskX.core.engine;

import java.util.List;
import java.util.UUID;

/**
 * 一次动作处理的结果，交给调用方决定后续表现（提示、刷新 GUI 等）。
 * <p>
 * 引擎只负责「改了什么」，不负责「怎么告诉玩家」——
 * 这样进度更新可以脱离表现层单独测试。
 *
 * @param playerId        触发本次动作的玩家
 * @param changed         是否有进度变化
 * @param completedQuests 本次刚刚达成全部目标的任务 id（可能是多个）
 */
public record ApplyResult(UUID playerId, boolean changed, List<String> completedQuests) {

    public static final ApplyResult NONE = new ApplyResult(null, false, List.of());

    public static ApplyResult changed(UUID playerId) {
        return new ApplyResult(playerId, true, List.of());
    }

    public static ApplyResult none(UUID playerId) {
        return new ApplyResult(playerId, false, List.of());
    }

    public boolean hasCompletion() {
        return !completedQuests.isEmpty();
    }
}
