package com.playerPlugin.playerTaskX.api.schema;

/**
 * 配置字段的输入类型，决定网页编辑器与 GUI 渲染成什么控件。
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
    /** 材质名（编辑器提供材质选择） */
    MATERIAL,
    /** 实体类型名（编辑器提供下拉） */
    ENTITY,
    /** 方块或实体类型名皆可（编辑器同时列出两者） */
    TARGET,
    /** 下拉选择，候选项见 {@link ConfigField#options()} */
    ENUM
}
