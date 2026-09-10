package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;

import java.util.List;
import java.util.Map;

/** 目标：合成指定物品。{@code target} 为产物物品材质名，支持逗号分隔多值，留空或 * 表示任意合成。 */
public final class CraftObjective implements ObjectiveType {

    @Override
    public String id() {
        return "craft";
    }

    @Override
    public String displayName() {
        return "合成物品";
    }

    @Override
    public Trigger trigger() {
        return Trigger.CRAFT;
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.material("target", "目标物品", "DIAMOND"),
                ConfigField.amount(1)
        );
    }

    /**
     * 判定本次合成产物是否命中配置：命中返回本次合成数量（如一次合成 4 个则记 4），否则返回 0。
     *
     * @param context    动作上下文，{@code target} 为产物物品材质名
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
