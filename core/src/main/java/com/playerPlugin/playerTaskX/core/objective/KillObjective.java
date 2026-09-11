package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;

import java.util.List;
import java.util.Map;

/** 目标：击杀指定生物或玩家。{@code target} 为实体类型名，支持逗号分隔多值，留空或 * 表示任意生物。 */
public final class KillObjective implements ObjectiveType {

    @Override
    public String id() {
        return "kill";
    }

    @Override
    public String displayName() {
        return "击杀生物";
    }

    @Override
    public Trigger trigger() {
        return Trigger.KILL;
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.optionalEntity("target", "生物类型", "ZOMBIE"),
                ConfigField.amount(1)
        );
    }

    /**
     * 判定本次击杀的实体是否命中配置：命中返回本次数量，否则返回 0。
     *
     * @param context    动作上下文，{@code target} 为被击杀实体的类型名
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
