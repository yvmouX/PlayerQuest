package com.playerPlugin.playerTaskX.core.storage;

/**
 * 试图修改「文件里定义的定义」时抛出。
 *
 * <p>YAML 文件（{@code quests/} 与 {@code presets/}）是<b>只读</b>来源：
 * 游戏内命令与网页编辑器只修改数据库里的定义，否则「文件与库哪个是权威」立刻变成一团乱麻。
 *
 * <p>继承 {@link IllegalStateException}：对编辑器来说这是「当前状态不允许这么做」，
 * 而不是请求格式错误（400）或存储故障（500），因此 {@code EditorApi} 会把它映射成 409
 * 并把消息原样带给管理员。
 */
public class DefinitionReadOnlyException extends IllegalStateException {

    public DefinitionReadOnlyException(String message) {
        super(message);
    }
}
