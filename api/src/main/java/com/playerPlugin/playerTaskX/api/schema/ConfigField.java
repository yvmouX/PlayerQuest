package com.playerPlugin.playerTaskX.api.schema;

import java.util.List;

/**
 * 目标/奖励类型的单个配置字段：键、显示名、说明、控件形状、值域。类型自描述，校验与游戏内编辑器都读它。
 * 形状决定编辑器弹什么控件（{@link Shape#CANDIDATES} 走候选列表，其余走聊天输入），值域决定候选项与校验。
 */
public record ConfigField(
        String key,
        String label,
        String hint,
        Shape shape,
        List<ValueKind> kinds
) {

    /** 编辑器给该字段用的输入控件。 */
    public enum Shape {
        /** 任意文本。 */
        TEXT,
        /** 整数，如数量、次数。 */
        INTEGER,
        /** 小数，如金额、尺寸。 */
        DECIMAL,
        /** 开关（是/否），如「以玩家身份执行」。 */
        BOOLEAN,
        /** 从候选清单里挑（材质 / 实体 / 附魔 / 鱼…），必须有值域。 */
        CANDIDATES
    }

    public ConfigField {
        kinds = kinds == null ? List.of() : List.copyOf(kinds);
        if ((shape == Shape.CANDIDATES) != !kinds.isEmpty()) {
            throw new IllegalArgumentException(
                    key + " 的字段形状与值域不符：候选字段必须给值域，其余形状不该有值域");
        }
    }

    /** 任意文本。 */
    public static ConfigField text(String key, String label, String hint) {
        return new ConfigField(key, label, hint, Shape.TEXT, List.of());
    }

    /** 整数。 */
    public static ConfigField integer(String key, String label, String hint) {
        return new ConfigField(key, label, hint, Shape.INTEGER, List.of());
    }

    /** 小数。 */
    public static ConfigField decimal(String key, String label, String hint) {
        return new ConfigField(key, label, hint, Shape.DECIMAL, List.of());
    }

    /** 开关。 */
    public static ConfigField bool(String key, String label, String hint) {
        return new ConfigField(key, label, hint, Shape.BOOLEAN, List.of());
    }

    /**
     * 候选字段（材质 / 实体 / 附魔 / 鱼…）。
     *
     * @param kinds 取值域，多个之间是「或」；至少给一个
     */
    public static ConfigField of(String key, String label, String hint, ValueKind... kinds) {
        if (kinds == null || kinds.length == 0) {
            throw new IllegalArgumentException(key + " 声明了候选字段却没有值域，应当用 ConfigField.text(...)");
        }
        return new ConfigField(key, label, hint, Shape.CANDIDATES, List.of(kinds));
    }

    /** 目标数量字段，所有目标类型共用，因此单独提供。 */
    public static ConfigField amount() {
        return integer("amount", "所需数量", "完成该目标需要的次数");
    }
}
