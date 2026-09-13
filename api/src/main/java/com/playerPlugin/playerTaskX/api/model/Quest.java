package com.playerPlugin.playerTaskX.api.model;

import java.util.List;

/**
 * 任务定义（静态数据，由配置文件或网页编辑器维护）。
 *
 * @param id            唯一标识，如 {@code daily_mine_64}
 * @param name          显示名（支持 MiniMessage / &amp; 颜色码）
 * @param description   描述，按行
 * @param icon          图标材质名，如 {@code DIAMOND_PICKAXE}
 * @param category      分类，用于 GUI 与编辑器分组，可为空
 * @param type          任务类型
 * @param prerequisites 前置任务 id 列表，全部<b>已领取奖励</b>后本任务才可被抽取/领取；空表示无前置
 * @param objectives    任务目标，至少一个
 * @param rewards       任务奖励，可为空
 * @param refreshCost   刷新费用（仅 {@link QuestType#DAILY} 有意义），0 表示不可刷新
 * @param enabled       是否启用（禁用后不再被抽取/展示）
 */
public record Quest(
        String id,
        String name,
        List<String> description,
        String icon,
        String category,
        QuestType type,
        List<String> prerequisites,
        List<QuestObjective> objectives,
        List<QuestReward> rewards,
        double refreshCost,
        boolean enabled
) {

    public Quest {
        description = description == null ? List.of() : List.copyOf(description);
        prerequisites = normalizeIds(prerequisites);
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        type = type == null ? QuestType.NORMAL : type;
        icon = (icon == null || icon.isBlank()) ? "PAPER" : icon;
    }

    /**
     * 前置 id 规范化：去空白、去空串、去重，<b>保留顺序</b>。
     * <p>
     * 顺序不影响判定（判定是「全部满足」），保留它只是为了让编辑器里的排列稳定。
     * 自引用与成环属于配置错误，由校验报出来，<b>不在这里静默删掉</b>——
     * 悄悄移除一个前置会让管理员以为自己配的关系生效了。
     */
    private static List<String> normalizeIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> ids = new java.util.ArrayList<>(raw.size());
        for (String id : raw) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String trimmed = id.trim();
            if (!ids.contains(trimmed)) {
                ids.add(trimmed);
            }
        }
        return List.copyOf(ids);
    }

    public boolean isDaily() {
        return type == QuestType.DAILY;
    }

    /** 是否有前置任务。 */
    public boolean hasPrerequisites() {
        return !prerequisites.isEmpty();
    }

    /** 是否结构完整、可以投入使用。 */
    public boolean isUsable() {
        return id != null && !id.isBlank() && !objectives.isEmpty();
    }

    /**
     * 复制一份并改写启用状态。
     * <p>
     * record 没有 setter，而「切换启用」是管理命令与管理界面共用的写操作：
     * 让每个调用点各自 {@code new Quest(…11 个字段…)}，改一次字段就要改所有调用点。
     */
    public Quest withEnabled(boolean enabled) {
        return new Quest(id, name, description, icon, category, type, prerequisites,
                objectives, rewards, refreshCost, enabled);
    }
}
