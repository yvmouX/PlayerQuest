package com.playerPlugin.playerTaskX.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;

/**
 * 事件回调管理器
 * 负责管理和触发事件回调
 */
public class EventCallbackManager {
    private static volatile EventCallbackManager eventCallbackManager;
    
    // 存储不同事件类型的回调列表
    private final Map<Class<?>, List<EventCallback<?>>> callbacks = new ConcurrentHashMap<>();
    
    private EventCallbackManager() {}
    
    public static EventCallbackManager getInstance() {
        if (eventCallbackManager == null) {
            synchronized (EventCallbackManager.class) {
                if (eventCallbackManager == null) {
                    eventCallbackManager = new EventCallbackManager();
                }
            }
        }
        return eventCallbackManager;
    }
    
    /**
     * 注册事件回调
     * @param eventType 事件类型
     * @param callback 回调处理器
     * @param <T> 事件泛型
     */
    public <T> void registerCallback(Class<T> eventType, EventCallback<T> callback) {
        callbacks.computeIfAbsent(eventType, k -> new ArrayList<>()).add(callback);
        log.info("已注册事件回调: " + eventType.getSimpleName() + " -> " + callback.getClass().getSimpleName());
    }
    
    /**
     * 触发事件回调
     * @param event 事件对象
     * @param <T> 事件泛型
     */
    @SuppressWarnings("unchecked")
    public <T> void triggerCallbacks(T event) {
        if (event == null) return;
        
        List<EventCallback<?>> eventCallbacks = callbacks.get(event.getClass());
        if (eventCallbacks != null && !eventCallbacks.isEmpty()) {
            for (EventCallback<?> callback : eventCallbacks) {
                try {
                    ((EventCallback<T>) callback).onEvent(event);
                } catch (Exception e) {
                    log.err("执行事件回调时发生错误: " + callback.getClass().getSimpleName() + " - " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 获取指定事件类型的回调数量
     * @param eventType 事件类型
     * @return 回调数量
     */
    public int getCallbackCount(Class<?> eventType) {
        List<EventCallback<?>> eventCallbacks = callbacks.get(eventType);
        return eventCallbacks != null ? eventCallbacks.size() : 0;
    }
    
    /**
     * 清除所有回调
     */
    public void clearAllCallbacks() {
        callbacks.clear();
        log.info("已清除所有事件回调");
    }
    
    /**
     * 清除指定事件类型的回调
     * @param eventType 事件类型
     */
    public void clearCallbacks(Class<?> eventType) {
        callbacks.remove(eventType);
        log.info("已清除事件回调: " + eventType.getSimpleName());
    }
}
