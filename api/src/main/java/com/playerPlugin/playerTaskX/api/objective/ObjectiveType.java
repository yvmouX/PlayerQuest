package com.playerPlugin.playerTaskX.api.objective;

import com.playerPlugin.playerTaskX.api.schema.ConfigurableType;

/**
 * 任务目标类型——扩展点之一。
 * <p>
 * 新增一种目标只需实现本接口并注册，引擎、GUI、网页编辑器都不需要改动：
 * 界面由 {@link #schema()} 自动生成表单，判定由 {@link #match} 完成。
 */
public interface ObjectiveType extends ConfigurableType {

    /** 该类型响应的动作；引擎据此把动作只派发给相关类型。 */
    Trigger trigger();

    /**
     * 判定本次动作带来多少进度。
     * <p>
     * 实现要求：<b>纯函数、不产生副作用</b>（不要在这里发奖励或发消息，那是引擎的职责），
     * 也不要访问 Bukkit 之外的全局状态。
     *
     * @param context    动作上下文
     * @param properties 该目标的配置
     * @return 递增的进度值，0 表示本次动作不命中该目标
     */
    int match(ProgressContext context, java.util.Map<String, Object> properties);

    /**
     * 默认的目标命中判定：ignoring case 比较 {@code target} 字段。
     * 支持 {@code *} 或空值表示「任意」，多数简单类型直接复用即可。
     */
    default boolean targetMatches(ProgressContext context, java.util.Map<String, Object> properties) {
        Object configured = properties.get("target");
        String target = configured == null ? "" : String.valueOf(configured).trim();
        if (target.isEmpty() || "*".equals(target)) {
            return true;
        }
        String actual = context.target();
        if (actual == null) {
            return false;
        }
        // 支持逗号分隔的多值配置，如 "DIAMOND_ORE,DEEPSLATE_DIAMOND_ORE"
        for (String candidate : target.split(",")) {
            if (candidate.trim().equalsIgnoreCase(actual)) {
                return true;
            }
        }
        return false;
    }
}
