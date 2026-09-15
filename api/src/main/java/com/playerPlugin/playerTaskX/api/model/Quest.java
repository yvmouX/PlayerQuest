package com.playerPlugin.playerTaskX.api.model;

import java.util.List;

/** 任务定义（静态数据，来自数据库或只读的 {@code quests/*.yml}）；{@code refreshCost} 为 0 表示不可刷新。 */
public record Quest(
        String id,
        String name,
        List<String> description,
        String icon,
        String category,
        QuestType type,
        List<QuestObjective> objectives,
        List<QuestReward> rewards,
        double refreshCost,
        boolean enabled
) {

    public Quest {
        description = description == null ? List.of() : List.copyOf(description);
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        type = type == null ? QuestType.NORMAL : type;
        icon = (icon == null || icon.isBlank()) ? "PAPER" : icon;
    }

    /** 是不是周期任务（每日/每周/每月/自定义）：会被抽取、会过期、可刷新。 */
    public boolean isPeriodic() {
        return type.isPeriodic();
    }

    /** 是否结构完整、可以投入使用。 */
    public boolean isUsable() {
        return id != null && !id.isBlank() && !objectives.isEmpty();
    }

    /**
     * 复制一份并改写启用状态。
     * <p>
     * record 没有 setter，而「切换启用」是管理命令与管理界面共用的写操作：
     * 让每个调用点各自 {@code new Quest(…10 个字段…)}，改一次字段就要改所有调用点。
     */
    public Quest withEnabled(boolean enabled) {
        return new Quest(id, name, description, icon, category, type,
                objectives, rewards, refreshCost, enabled);
    }
}
