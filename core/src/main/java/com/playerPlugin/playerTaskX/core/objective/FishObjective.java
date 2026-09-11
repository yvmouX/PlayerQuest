package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;

import java.util.List;
import java.util.Map;

/** 目标：垂钓。{@code target} 为钓获物的物品材质名，可留空表示任意鱼类。 */
public final class FishObjective implements ObjectiveType {

    @Override
    public String id() {
        return "fish";
    }

    @Override
    public String displayName() {
        return "垂钓";
    }

    @Override
    public Trigger trigger() {
        return Trigger.FISH;
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.optionalMaterial("target", "钓获物", ""),
                ConfigField.amount(1)
        );
    }

    /**
     * 判定本次钓获物是否命中配置：命中返回本次数量，否则返回 0。
     * <p>
     * {@code context.target()} 可能为 {@code null}（例如钓上宝藏但监听器未给出物品），
     * 此时只有「留空 / *」的配置才算命中。
     *
     * @param context    动作上下文，{@code target} 为鱼的物品材质名，可为 null
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
