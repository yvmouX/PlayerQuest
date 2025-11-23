package com.playerPlugin.playerTaskX.event;

import java.util.function.Consumer;

public interface EventBus {
    <E> void register(Class<E> eventClass, Consumer<E> handler);

    <E> void unregister(Class<E> eventClass, Consumer<E> handler);

    void post(Object event);

    void postAsync(Object event);

    void shutdown();
}
