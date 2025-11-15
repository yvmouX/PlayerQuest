package com.playerPlugin.playerTaskX.EventHandlers.services;

import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

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
