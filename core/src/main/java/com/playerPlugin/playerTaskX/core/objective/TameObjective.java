package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;

import java.util.List;
import java.util.Map;

/** 目标：驯服指定生物。 */
public final class TameObjective implements ObjectiveType {

    @Override
    public String id() {
        return "tame";
    }

    @Override
    public String displayName() {
        return "驯服";
    }

    @Override
    public Trigger trigger() {
        return Trigger.TAME;
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.optionalEntity("target", "生物类型", "WOLF"),
                ConfigField.amount(1)
        );
    }

    @Override
    public int match(ProgressContext context, Map<String, Object> properties) {
        if (!targetMatches(context, properties)) {
            return 0;
        }
        return context.amount();
    }
}
