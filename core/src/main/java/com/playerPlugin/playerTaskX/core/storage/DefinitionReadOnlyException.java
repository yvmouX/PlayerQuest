package com.playerPlugin.playerTaskX.core.storage;

/**
 * 试图修改「文件里定义的定义」时抛出：YAML 目录只读，游戏内命令与 GUI 只改数据库里的定义。
 * 继承 {@link IllegalStateException}，命令与 GUI 直接把 {@link #getMessage()} 讲给管理员听。
 */
public class DefinitionReadOnlyException extends IllegalStateException {

    public DefinitionReadOnlyException(String message) {
        super(message);
    }
}
