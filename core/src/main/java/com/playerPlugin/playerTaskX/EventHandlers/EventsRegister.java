package com.playerPlugin.playerTaskX.EventHandlers;

import com.playerPlugin.playerTaskX.EventHandlers.handlers.PlayerJoinHandler;
import com.playerPlugin.playerTaskX.EventHandlers.handlers.task.TaskEventHandler;
import com.playerPlugin.playerTaskX.EventHandlers.services.impl.PlaceService;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.EventHandlers.services.impl.BreakService;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import static com.playerPlugin.playerTaskX.utils.Help.log;

public class EventsRegister {
    private static TaskManager tm;
    private static StorgeManager sm;
    private static volatile EventCallbackManager callbackManager;

    public EventsRegister(TaskManager tm, StorgeManager sm) {
        EventsRegister.tm = tm;
        EventsRegister.sm = sm;
    }

    public static EventCallbackManager getCallbackManager() {return callbackManager;}


    /**
     * 注册
     *
     */
    public static void register(PlayerTaskX plugin) {
        // 注册事件监听器
        Class<?>[] handlerClasses = {
                PlayerJoinHandler.class,
                TaskEventHandler.class,
        };

        for (Class<?> handlerClass : handlerClasses) {
            try {
                Listener.class.isAssignableFrom(handlerClass);
                Listener listener = (Listener) handlerClass.getDeclaredConstructor().newInstance();
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
                log.info("已注册事件监听器: " + handlerClass.getSimpleName());
            } catch (Exception e) {
                log.error("注册事件监听器失败: " + handlerClass.getSimpleName() + " - " + e.getMessage());
            }
        }

        // 注册事件回调服务
        registerEventCallbacks();
    }

    /**
     * 注册事件回调服务
     */
    private static void registerEventCallbacks() {
        callbackManager = EventCallbackManager.getInstance();

        try {
            // 注册破坏方块事件的回调服务
            callbackManager.registerCallback(BlockBreakEvent.class, new BreakService(tm, sm));
            // 注册放置方块事件的回调服务
            callbackManager.registerCallback(BlockPlaceEvent.class, new PlaceService(tm, sm));



            log.info("已注册所有事件回调服务");
        } catch (Exception e) {
            log.error("注册事件回调服务失败: " + e.getMessage());
        }
    }
}
