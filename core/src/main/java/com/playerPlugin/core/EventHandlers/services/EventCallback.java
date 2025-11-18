package com.playerPlugin.core.EventHandlers.services;

/**
 * 事件回调接口
 * @param <T> 事件类型
 */
public interface EventCallback<T> {
    /**
     * 处理事件
     * @param event 事件对象
     */
    void onEvent(T event);
}
