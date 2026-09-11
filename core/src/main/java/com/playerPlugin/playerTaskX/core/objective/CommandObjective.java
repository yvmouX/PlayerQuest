package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;

import java.util.List;
import java.util.Map;

/** 目标：执行指定命令。{@code target} 为不带前导斜杠的命令名（如 home），留空或 * 表示任意命令。 */
public final class CommandObjective implements ObjectiveType {

    @Override
    public String id() {
        return "command";
    }

    @Override
    public String displayName() {
        return "执行命令";
    }

    @Override
    public Trigger trigger() {
        return Trigger.COMMAND;
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.optionalText("target", "命令名", "home", "不带前导斜杠的命令名，如 home；留空或 * 表示任意命令"),
                ConfigField.amount(1)
        );
    }

    /**
     * 判定本次执行的命令是否命中配置：命中返回本次数量，否则返回 0。
     * <p>
     * 按命令名相等匹配（忽略大小写，支持逗号分隔多值）：配置里不要写前导 {@code /}，
     * 由监听器负责把 {@code context.target()} 归一化为不带斜杠的命令名。
     *
     * @param context    动作上下文，{@code target} 为命令名（不含前导 /）
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
