package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;

import java.util.List;
import java.util.Map;

/** 目标：交互（左右键点击方块或实体）。{@code target} 为方块/实体类型名，可留空表示任意对象；{@code mode} 限定交互方式。 */
public final class InteractObjective implements ObjectiveType {

    @Override
    public String id() {
        return "interact";
    }

    @Override
    public String displayName() {
        return "交互";
    }

    @Override
    public Trigger trigger() {
        return Trigger.INTERACT;
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.text("target", "交互对象", "", "方块或实体类型名，如 CHEST、VILLAGER；留空或 * 表示任意对象"),
                ConfigField.options("mode", "交互方式", "ANY",
                        List.of("ANY", "LEFT_CLICK_BLOCK", "RIGHT_CLICK_BLOCK", "RIGHT_CLICK_ENTITY", "LEFT_CLICK_ENTITY"),
                        "限定交互方式，ANY 表示不限"),
                ConfigField.amount(1)
        );
    }

    /**
     * 判定本次交互是否同时命中 {@code mode} 与 {@code target}：命中返回本次数量，否则返回 0。
     *
     * @param context    动作上下文，{@code target} 为方块/实体类型名，{@code extra} 为交互方式
     * @param properties 目标配置，读取 {@code target} 与 {@code mode}
     */
    @Override
    public int match(ProgressContext context, Map<String, Object> properties) {
        if (!modeMatches(context, properties)) {
            return 0;
        }
        if (!targetMatches(context, properties)) {
            return 0;
        }
        return context.amount();
    }

    /** 交互方式判定：{@code ANY} 或留空表示不限，否则要求与 {@code context.extra()} 忽略大小写相等。 */
    private static boolean modeMatches(ProgressContext context, Map<String, Object> properties) {
        String mode = str(properties, "mode").trim();
        if (mode.isEmpty() || "ANY".equalsIgnoreCase(mode)) {
            return true;
        }
        String actual = context.extra();
        return actual != null && mode.equalsIgnoreCase(actual.trim());
    }

    /** 读取配置字符串，null 安全：缺失或空值一律返回空串。 */
    private static String str(Map<String, Object> properties, String key) {
        Object value = properties == null ? null : properties.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
