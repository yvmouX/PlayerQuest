package com.playerPlugin.playerTaskX.EventHandlers;

import com.playerPlugin.playerTaskX.EventHandlers.handlers.PlayerJoinHandler;
import com.playerPlugin.playerTaskX.EventHandlers.handlers.task.TaskEventHandler;
import com.playerPlugin.playerTaskX.EventHandlers.services.impl.PlaceService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.EventHandlers.services.impl.BreakService;
import com.playerPlugin.playerTaskX.structs.eventStructs.BreakEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;

public class EventsRegister {
    private static volatile EventCallbackManager callbackManager;

    public static EventCallbackManager getCallbackManager() {return callbackManager;}


    /**
     * 注册
     *
     */
    public static void register() {
        PlayerTaskX instance = PlayerTaskX.getInstance();
        // 注册事件监听器
        Class<?>[] handlerClasses = {
                PlayerJoinHandler.class,
                TaskEventHandler.class,
        };

        for (Class<?> handlerClass : handlerClasses) {
            try {
                Listener.class.isAssignableFrom(handlerClass);
                Listener listener = (Listener) handlerClass.getDeclaredConstructor().newInstance();
                instance.getServer().getPluginManager().registerEvents(listener, instance);
                log.info("已注册事件监听器: " + handlerClass.getSimpleName());
            } catch (Exception e) {
                log.err("注册事件监听器失败: " + handlerClass.getSimpleName() + " - " + e.getMessage());
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
            callbackManager.registerCallback(BreakEvent.class, new BreakService());
            // 注册放置方块事件的回调服务
            callbackManager.registerCallback(BlockPlaceEvent.class, new PlaceService());



            log.info("已注册所有事件回调服务");
        } catch (Exception e) {
            log.err("注册事件回调服务失败: " + e.getMessage());
        }
    }
}
