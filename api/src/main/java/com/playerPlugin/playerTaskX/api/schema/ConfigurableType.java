package com.playerPlugin.playerTaskX.api.schema;

import java.util.List;

/**
 * 「由配置驱动」的类型：目标类型与奖励类型共有的形状。
 *
 * <p>两者在引擎里是完全不同的东西（一个判定进度、一个发放奖励），但凡是
 * <b>展示与编辑</b>它们的地方——网页编辑器的动态表单、GUI 的图标推导与字段说明、
 * 管理员命令的类型名回显——需要的都只是这三件事。抽出这一层后，这些地方
 * 可以只写一份实现，而不必为目标与奖励各写一份近乎相同的代码。
 */
public interface ConfigurableType {

    /** 唯一 id，配置里 {@code type} 字段写的就是它，如 {@code break_block} / {@code money}。 */
    String id();

    /** 显示名，用于 GUI 与编辑器。 */
    String displayName();

    /** 配置字段描述，用于自动生成表单。 */
    List<ConfigField> schema();
}
