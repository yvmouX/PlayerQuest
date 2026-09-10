package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;

import java.util.List;
import java.util.Map;

/** 目标：提交指定物品（在 GUI 中主动上交）。{@code target} 为物品材质名，支持逗号分隔多值，留空或 * 表示任意物品。 */
public final class SubmitObjective implements ObjectiveType {

    @Override
    public String id() {
        return "submit";
    }

    @Override
    public String displayName() {
        return "提交物品";
    }

    @Override
    public Trigger trigger() {
        return Trigger.SUBMIT;
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.material("target", "提交物品", "DIAMOND"),
                ConfigField.amount(1)
        );
    }

    /**
     * 判定本次提交的物品是否命中配置：命中返回本次提交数量，否则返回 0。
     * <p>
     * {@code context.extra()} 对本类型无意义（可为 {@code null}），判定只看 {@code target}。
     *
     * @param context    动作上下文，{@code target} 为被提交物品的材质名
     * @param properties 目标配置，读取 {@code target}
     */
    @Override
    public int match(ProgressContext context, Map<String, Object> properties) {
        if (!targetMatches(context, properties)) {
            return 0;
        }
        return context.amount();
    }
}
