package com.playerPlugin.playerTaskX.api.schema;

import java.util.List;

/** 「由配置驱动」的类型：目标与奖励共有的形状（id + 显示名 + 字段表），展示它们的地方只需这三样。 */
public interface ConfigurableType {

    /** 唯一 id，配置里 {@code type} 字段写的就是它，如 {@code break_block} / {@code money}。 */
    String id();

    /** 显示名，用于 GUI 与管理命令回显。 */
    String displayName();

    /** 配置字段描述，用于自动生成表单。 */
    List<ConfigField> schema();
}
