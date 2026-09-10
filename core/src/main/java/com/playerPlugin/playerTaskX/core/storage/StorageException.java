package com.playerPlugin.playerTaskX.core.storage;

/**
 * 存储层异常：把 SQLException 包装成运行时异常，避免仓储接口到处声明受检异常。
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
