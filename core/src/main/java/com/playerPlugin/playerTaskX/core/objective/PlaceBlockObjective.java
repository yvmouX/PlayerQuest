package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;

import java.util.List;
import java.util.Map;

/** 目标：放置指定方块。{@code target} 为方块材质名，支持逗号分隔多值，留空或 * 表示任意方块。 */
public final class PlaceBlockObjective implements ObjectiveType {

    @Override
    public String id() {
        return "place_block";
    }

    @Override
    public String displayName() {
        return "放置方块";
    }

    @Override
    public Trigger trigger() {
        return Trigger.PLACE_BLOCK;
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.material("target", "目标方块", "STONE"),
                ConfigField.amount(64)
        );
    }

    /**
     * 判定本次放置的方块是否命中配置：命中返回本次数量，否则返回 0。
     *
     * @param context    动作上下文，{@code target} 为被放置方块的材质名
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
