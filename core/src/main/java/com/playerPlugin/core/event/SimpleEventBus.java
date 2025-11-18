package com.playerPlugin.core.event;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static com.playerPlugin.core.utils.Help.log;

public class SimpleEventBus implements EventBus{
    private final ConcurrentMap<Class<?>, CopyOnWriteArrayList<Consumer<Object>>> handlers = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public SimpleEventBus() {
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "playerTaskX-eventbus" + UUID.randomUUID());
            t.setDaemon(true);
            return t;
        });
    }


    @Override
    @SuppressWarnings("unchecked")
    public <E> void register(Class<E> eventClass, Consumer<E> handler) {
        handlers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add((Consumer<Object>) handler);
    }

    @Override
    public <E> void unregister(Class<E> eventClass, Consumer<E> handler) {
        List<Consumer<Object>> list = handlers.get(eventClass);
        if (list != null) list.remove(handler);
    }

    /**
     * 同步发布事件，按注册顺序执行订阅者。
     * 推荐用于非耗时操作，如更新缓存、通知监听器等。
     *
     * @param event 事件
     */
    @Override
    public void post(Object event) {
        List<Consumer<Object>> list = handlers.getOrDefault(event.getClass(), new CopyOnWriteArrayList<>());
        for (Consumer<Object> h : list) {
            try {
                h.accept(event);
            } catch (Exception e) {
                log.error(String.format("事件处理失败：异步=%s 事件类型=%s, 订阅者=%s, 异常=%s", "否", event.getClass().getName(), h.getClass().getName(), e));
            }
        }
    }

    /**
     * 异步发布事件，按注册顺序并发执行订阅者。
     * 推荐用于耗时操作，如数据库交互、网络请求等。
     *
     * @param event 事件
     */
    @Override
    public void postAsync(Object event) {
        List<Consumer<Object>> list = handlers.getOrDefault(event.getClass(), new CopyOnWriteArrayList<>());
        for (Consumer<Object> h : list) {
            executor.submit(() -> {
                try {
                    h.accept(event);
                } catch (Exception e) {
                    log.error(String.format("事件处理失败：异步=%s 事件类型=%s, 订阅者=%s, 异常=%s", "是", event.getClass().getName(), h.getClass().getName(), e));
                }
            });
        }
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
