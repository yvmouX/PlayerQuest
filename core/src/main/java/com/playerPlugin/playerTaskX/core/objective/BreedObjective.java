package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;

import java.util.List;
import java.util.Map;

/** 目标：繁殖（喂食两只成年动物产生幼崽）。{@code target} 为幼崽实体类型名，可留空表示任意实体。 */
public final class BreedObjective implements ObjectiveType {

    @Override
    public String id() {
        return "breed";
    }

    @Override
    public String displayName() {
        return "繁殖";
    }

    @Override
    public Trigger trigger() {
        return Trigger.BREED;
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.entity("target", "幼崽实体", ""),
                ConfigField.amount(1)
        );
    }

    /**
     * 判定本次繁殖出的幼崽是否命中配置：命中返回本次数量，否则返回 0。
     * <p>
     * {@code context.target()} 为幼崽实体类型名，可能为 {@code null}，
     * 此时只有「留空 / *」的配置才算命中。
     *
     * @param context    动作上下文，{@code target} 为幼崽实体类型名，可为 null
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
