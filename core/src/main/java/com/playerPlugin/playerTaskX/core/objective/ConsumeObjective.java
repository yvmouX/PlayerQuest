package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;

import java.util.List;
import java.util.Map;

/** 目标：消耗（吃掉/喝掉）指定物品。{@code target} 为物品材质名，支持逗号分隔多值，留空或 * 表示任意物品。 */
public final class ConsumeObjective implements ObjectiveType {

    @Override
    public String id() {
        return "consume";
    }

    @Override
    public String displayName() {
        return "消耗物品";
    }

    @Override
    public Trigger trigger() {
        return Trigger.CONSUME;
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.material("target", "目标物品", "BREAD"),
                ConfigField.amount(1)
        );
    }

    /**
     * 判定本次消耗的物品是否命中配置：命中返回本次消耗数量，否则返回 0。
     *
     * @param context    动作上下文，{@code target} 为被消耗物品的材质名
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
