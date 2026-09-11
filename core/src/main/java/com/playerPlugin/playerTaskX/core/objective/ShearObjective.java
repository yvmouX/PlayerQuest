package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;

import java.util.List;
import java.util.Map;

/** 目标：剪切（剪羊毛、剪蘑菇等）。{@code target} 为被剪实体类型名，可留空表示任意实体。 */
public final class ShearObjective implements ObjectiveType {

    @Override
    public String id() {
        return "shear";
    }

    @Override
    public String displayName() {
        return "剪切";
    }

    @Override
    public Trigger trigger() {
        return Trigger.SHEAR;
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.entity("target", "被剪实体", ""),
                ConfigField.amount(1)
        );
    }

    /**
     * 判定本次剪切的实体是否命中配置：命中返回本次数量，否则返回 0。
     * <p>
     * {@code context.target()} 为被剪实体类型名，可能为 {@code null}，
     * 此时只有「留空 / *」的配置才算命中。
     *
     * @param context    动作上下文，{@code target} 为被剪实体类型名，可为 null
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
