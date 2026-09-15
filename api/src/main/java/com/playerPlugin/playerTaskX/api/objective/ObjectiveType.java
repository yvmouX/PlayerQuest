package com.playerPlugin.playerTaskX.api.objective;

import com.playerPlugin.playerTaskX.api.schema.ConfigurableType;

import java.util.List;
import java.util.Map;

/** 任务目标类型——扩展点之一：实现并注册即可，界面读 {@link #schema()}、判定走 {@link #match()}。 */
public interface ObjectiveType extends ConfigurableType {

    /** 该类型响应的动作；引擎据此把动作只派发给相关类型。 */
    Trigger trigger();

    /** 判定本次动作带来多少进度；实现必须是纯函数（不发奖励、不发消息），返回 0 表示不命中。 */
    int match(ProgressContext context, Map<String, Object> properties);

    /** 默认的目标命中判定：忽略大小写比较 {@code target}，支持 {@code *}/留空表示任意、逗号多值，并一并比较 {@link ProgressContext#aliases()}。 */
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

    /** 该类型依赖的软依赖是否就绪；不可用时校验会报出原因（{@code /ptxa list} 与管理界面），而不是让人对着不涨进度的目标猜。 */
    default boolean available() {
        return true;
    }

    /** 不可用的原因，供 GUI 与校验提示；可用时返回空串。 */
    default String unavailableReason() {
        return "";
    }
}
