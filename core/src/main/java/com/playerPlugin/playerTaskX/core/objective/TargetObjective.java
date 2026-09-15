package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;

import java.util.List;
import java.util.Map;

/** 「一个 target 字段 + 一个数量」这类目标的统一实现：内置目标里 12 种行为相同，只差 id、动作与 target 的语义类型。 */
public record TargetObjective(String id, String displayName, Trigger trigger,
                              ConfigField target) implements ObjectiveType {

    @Override
    public List<ConfigField> schema() {
        return List.of(target, ConfigField.amount());
    }

    @Override
    public int match(ProgressContext context, Map<String, Object> properties) {
        return targetMatches(context, properties) ? context.amount() : 0;
    }
}
