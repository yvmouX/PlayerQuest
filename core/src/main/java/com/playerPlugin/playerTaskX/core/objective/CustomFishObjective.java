package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.core.integration.CustomFishingHook;

import java.util.List;
import java.util.Map;

/**
 * 目标：钓到 CustomFishing 的自定义鱼。
 *
 * <h2>为什么不复用 {@code fish}</h2>
 * CustomFishing 的钓获不是原版 {@code PlayerFishEvent} 的掉落物，钓到什么由它自己的
 * 战利品表决定（可能是自定义物品、可能是原版物品带自定义尺寸）。原版事件的
 * {@code getCaught()} 在设计上只能表达「一个物品堆」，表达不了「哪条自定义鱼、多大」，
 * 因此单独一种目标类型、单独一个动作 {@link Trigger#CUSTOM_FISH}，
 * 由 {@link CustomFishingHook} 注册的监听器推进。
 *
 * <h2>字段</h2>
 * <ul>
 *   <li>{@code target}：CustomFishing 的战利品 id（留空或 {@code *} 表示任意自定义鱼）；</li>
 *   <li>{@code min-size}：最小尺寸，0 表示不限——钓获尺寸是 CustomFishing 的核心玩法，
 *       把「钓到一条 30cm 以上的鱼」写成一个任务目标是很自然的诉求；</li>
 *   <li>{@code amount}：数量。</li>
 * </ul>
 *
 * <p>本类<b>不</b>引用任何 CustomFishing 的类型：它只读 {@link ProgressContext} 里的字符串，
 * 因此没有装 CustomFishing 的服务端也能安全地加载它（只会在编辑器/校验里提示不可用）。
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
                ConfigField.optionalText("target", "鱼 id", "",
                        "CustomFishing 战利品表里的 id，如 my_custom_fish；留空或 * 表示任意自定义鱼"),
                ConfigField.optionalDecimal(SIZE, "最小尺寸", 0.0,
                        "只统计尺寸不小于该值的钓获；0 表示不限（尺寸由 CustomFishing 提供）"),
                ConfigField.amount(1)
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

    /**
     * 本次钓获的尺寸。
     * <p>
     * 尺寸由监听器写在 {@link ProgressContext#extra()} 里（字符串形式）：
     * 取不到时按 0 处理，即「不知道尺寸」在配了最小尺寸的目标下视为不命中——
     * 宁可不给进度，也不要把一条小鱼算成达标。
     */
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
