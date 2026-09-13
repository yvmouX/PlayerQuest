package com.playerPlugin.playerTaskX.api.model;

import java.util.List;

/**
 * 任务定义（静态数据，由配置文件或网页编辑器维护）。
 *
 * @param id          唯一标识，如 {@code daily_mine_64}
 * @param name        显示名（支持 MiniMessage / &amp; 颜色码）
 * @param description 描述，按行
 * @param icon        图标材质名，如 {@code DIAMOND_PICKAXE}
 * @param category    分类，用于 GUI 与编辑器分组，可为空
 * @param type        任务类型
 * @param objectives  任务目标，至少一个
 * @param rewards     任务奖励，可为空
 * @param refreshCost 刷新费用（仅 {@link QuestType#DAILY} 有意义），0 表示不可刷新
 * @param enabled     是否启用（禁用后不再被抽取/展示）
 */
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

    public boolean isDaily() {
        return type == QuestType.DAILY;
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
