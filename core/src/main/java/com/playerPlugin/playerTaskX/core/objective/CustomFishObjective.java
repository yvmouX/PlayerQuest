package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import com.playerPlugin.playerTaskX.core.integration.customfishing.CustomFishingHook;

import java.util.List;
import java.util.Map;

/**
 * 目标：钓到 CustomFishing 的自定义鱼（{@code target} 是战利品 id，{@code min-size} 限定最小尺寸）。
 * 原版钓获事件只看得到掉落物、表达不了「哪条自定义鱼、多大」，故单独一种类型；本类不引用 CustomFishing 的类型，未装该插件的服务端也能安全加载。
 */
public final class CustomFishObjective implements ObjectiveType {

    public static final String ID = "custom_fish";

    /** 钓获尺寸的上下文键：由 CustomFishing 监听器放进 {@link ProgressContext#extra()}。 */
    public static final String SIZE = "min-size";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "自定义钓鱼";
    }

    @Override
    public Trigger trigger() {
        return Trigger.CUSTOM_FISH;
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                // 声明 FISH 而不是不给值域：校验据此拿 CustomFishing 注册表里的战利品 id 比对
                // （没装 CustomFishing 时清单为空，只能放行、不报错）
                ConfigField.of("target", "鱼 id",
                        "CustomFishing 战利品表里的 id，如 my_custom_fish；留空或 * 表示任意自定义鱼",
                        ValueKind.FISH),
                ConfigField.decimal(SIZE, "最小尺寸",
                        "只统计尺寸不小于该值的钓获；0 表示不限（尺寸由 CustomFishing 提供）"),
                ConfigField.amount()
        );
    }

    @Override
    public int match(ProgressContext context, Map<String, Object> properties) {
        if (!targetMatches(context, properties)) {
            return 0;
        }
        double required = number(properties.get(SIZE));
        if (required > 0 && size(context) < required) {
            return 0;
        }
        return context.amount();
    }

    /** 本次钓获的尺寸（监听器写在 {@link ProgressContext#extra()} 里）；取不到时按 0 处理，即在配了最小尺寸的目标下视为不命中，宁可不给进度也不算小鱼达标。 */
    private static double size(ProgressContext context) {
        return number(context.extra());
    }

    private static double number(Object raw) {
        if (raw instanceof Number value) {
            return value.doubleValue();
        }
        if (raw instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    @Override
    public boolean available() {
        return CustomFishingHook.supported();
    }

    @Override
    public String unavailableReason() {
        return available() ? "" : "未安装 CustomFishing";
    }
}
