package com.playerPlugin.playerTaskX.api.objective;

import com.playerPlugin.playerTaskX.api.schema.ConfigurableType;

import java.util.List;
import java.util.Map;

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
    int match(ProgressContext context, Map<String, Object> properties);

    /**
     * 默认的目标命中判定：忽略大小写比较 {@code target} 字段。
     * <p>
     * 支持 {@code *} 或空值表示「任意」、逗号分隔的多值配置（如
     * {@code DIAMOND_ORE,DEEPSLATE_DIAMOND_ORE}），并且会与
     * {@link ProgressContext#aliases()} 一并比较——同一只怪物的原版类型名与
     * MythicMobs 内部名都写在配置里时，命中任意一个即可。
     */
    default boolean targetMatches(ProgressContext context, Map<String, Object> properties) {
        Object configured = properties.get("target");
        String target = configured == null ? "" : String.valueOf(configured).trim();
        if (target.isEmpty() || "*".equals(target)) {
            return true;
        }
        String actual = context.target();
        List<String> aliases = context.aliases();
        // 支持逗号分隔的多值配置，如 "DIAMOND_ORE,DEEPSLATE_DIAMOND_ORE"
        for (String candidate : target.split(",")) {
            String trimmed = candidate.trim();
            if (trimmed.equalsIgnoreCase(actual)) {
                return true;
            }
            for (String alias : aliases) {
                if (trimmed.equalsIgnoreCase(alias)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 该类型当前是否可用（依赖的软依赖是否已安装）。
     * <p>
     * 对应 {@code RewardType.available()}：不可用时编辑器会标红、校验会报问题，
     * 而不是让管理员对着一个永远不涨进度的目标猜原因。
     */
    default boolean available() {
        return true;
    }

    /** 不可用的原因，供编辑器与校验提示；可用时返回空串。 */
    default String unavailableReason() {
        return "";
    }
}
