package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;

import java.util.List;
import java.util.Map;

/**
 * 「一个目标字段 + 一个数量」这一类目标的统一实现。
 *
 * <h2>为什么是一张表而不是十四个类</h2>
 * 14 种内置目标里有 12 种（挖掘/放置/合成/垂钓/击杀/消耗/附魔/剪切/繁殖/驯服/提交/命令）
 * 的行为完全相同：<b>配置里的 {@code target} 命中就加本次数量，否则加 0</b>。
 * 它们之间只差三件事实——id 与显示名、响应的动作、{@code target} 字段的语义类型
 * （材质 / 实体 / 自由文本）。为这三件事实各写一个 45 行的类，代价不是「多敲了几行」，
 * 而是改一处通用判定要动 12 个文件，且很容易漏掉其中一个。
 *
 * <p>拆成表之后，「新增一种简单目标」= 在 {@link com.playerPlugin.playerTaskX.core.registry.BuiltIns}
 * 里加一行，而不是加一个文件。真正有自己判定逻辑的类型（{@link InteractObjective}
 * 的 {@code mode} 匹配、{@link ChatObjective} 的包含匹配）仍然各自成类。
 *
 * <p>「类型自描述」这一性质没有损失：{@link #schema()} 依旧由本类型给出，
 * 编辑器与 GUI 照旧自动生成表单。
 *
 * @param id            类型 id，即配置里的 {@code type}
 * @param displayName   显示名
 * @param trigger       响应的动作
 * @param target        {@code target} 字段的描述（语义类型决定编辑器给不给选择器）
 * @param defaultAmount 新建配置时的默认数量
 */
public record TargetObjective(String id, String displayName, Trigger trigger,
                              ConfigField target, int defaultAmount) implements ObjectiveType {

    @Override
    public List<ConfigField> schema() {
        return List.of(target, ConfigField.amount(defaultAmount));
    }

    @Override
    public int match(ProgressContext context, Map<String, Object> properties) {
        return targetMatches(context, properties) ? context.amount() : 0;
    }
}
