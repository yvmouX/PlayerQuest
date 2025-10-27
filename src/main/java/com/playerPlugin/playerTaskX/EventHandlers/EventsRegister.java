package com.playerPlugin.playerTaskX.EventHandlers;

import com.playerPlugin.playerTaskX.EventHandlers.handlers.PlayerBreakHandler;
import com.playerPlugin.playerTaskX.EventHandlers.handlers.PlayerJoinHandler;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.services.EventCallbackManager;
import com.playerPlugin.playerTaskX.services.BreakService;
import com.playerPlugin.playerTaskX.structs.eventStructs.BreakEvent;
import org.bukkit.event.Listener;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;

public class EventsRegister {


    public static void register() {
        PlayerTaskX instance = PlayerTaskX.getInstance();
        // 注册事件监听器
        Class<?>[] handlerClasses = {
                PlayerJoinHandler.class,
                PlayerBreakHandler.class
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
        EventCallbackManager callbackManager = EventCallbackManager.getInstance();

        try {
            // 注册破坏方块事件的回调服务
            callbackManager.registerCallback(BreakEvent.class, new BreakService());

            log.info("已注册所有事件回调服务");
        } catch (Exception e) {
            log.err("注册事件回调服务失败: " + e.getMessage());
        }
    }
}
