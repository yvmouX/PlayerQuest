package com.playerPlugin.playerTaskX.api.schema;

/**
 * 配置字段的输入类型：决定网页编辑器与 GUI 渲染成什么控件。
 *
 * <h2>它只管「渲染成什么」</h2>
 * 值域（这个字段能填哪些值）在 {@link ConfigField#kinds()} 上，见 {@link ValueKind}。
 * 曾经这两件事混在一起——{@code MATERIAL} / {@code ENTITY} / {@code TARGET} / {@code FISH}
 * 既表示「弹选择器」又表示「列哪一份清单」，于是「列一份只有方块的清单」就必须新增一个枚举值，
 * 而前端也要跟着改。分开之后：加值域只动 {@link ValueKind} 与目标类型的 schema，
 * 控件类型始终只有这几种。
 */
public enum FieldType {
    /** 单行文本 */
    STRING,
    /** 整数 */
    INTEGER,
    /** 小数 */
    DECIMAL,
    /** 开关 */
    BOOLEAN,
    /**
     * 选择器：候选来自素材目录，值域见 {@link ConfigField#kinds()}。
     * <p>
     * 名字靠人记忆不现实（{@code DEEPSLATE_DIAMOND_ORE}、{@code craftengine:default:bench}），
     * 因此这类字段在编辑器里弹选择器；输入框本身仍可手打。
     */
    PICKER,
    /** 下拉选择，候选项见 {@link ConfigField#options()} */
    ENUM
}
