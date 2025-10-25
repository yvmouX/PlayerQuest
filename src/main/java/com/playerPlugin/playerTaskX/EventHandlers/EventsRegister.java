package com.playerPlugin.playerTaskX.EventHandlers;

import com.playerPlugin.playerTaskX.EventHandlers.handlers.PlayerJoinHandler;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.utils.Logger;
import org.bukkit.event.Listener;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;

public class EventsRegister {


    public static void register() {
        PlayerTaskX instance = PlayerTaskX.getInstance();
        Class<?>[] handlerClasses = {
            PlayerJoinHandler.class
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
    }
    }
