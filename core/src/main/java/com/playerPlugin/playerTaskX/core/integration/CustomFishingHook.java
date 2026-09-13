package com.playerPlugin.playerTaskX.core.integration;

import com.playerPlugin.playerTaskX.core.engine.ApplyResult;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * CustomFishing 接入点：注册钓获监听器。
 *
 * <h2>为什么监听器类要反射创建</h2>
 * {@link CustomFishingListener} 引用了 CustomFishing 的事件类型，被类加载时就会解析它们；
 * 没装 CustomFishing 的服务端上会抛 {@code NoClassDefFoundError}。
 * 因此本类<b>不 import 任何 CustomFishing 类型</b>，只在确认插件存在后才反射创建监听器并注册。
 *
 * <h2>对接方式</h2>
 * 钓到什么由 CustomFishing 自己的战利品表决定，原版 {@code PlayerFishEvent} 表达不了
 * 「哪条自定义鱼、多大」，因此监听它自己的 {@code FishingLootSpawnEvent}，
 * 把战利品 id 与尺寸翻译成 {@code CUSTOM_FISH} 动作（见 {@link CustomFishingListener}）。
 */
public final class CustomFishingHook {

    /** 插件名（软依赖探测与日志用）。 */
    public static final String PLUGIN = "CustomFishing";

    private static final String LISTENER_CLASS =
            "com.playerPlugin.playerTaskX.core.integration.CustomFishingListener";

    private CustomFishingHook() {
    }

    /** 服务端是否装了 CustomFishing（{@code custom_fish} 目标据此判断可用性）。 */
    public static boolean supported() {
        return SoftDependency.isPresent(PLUGIN);
    }

    /**
     * 注册钓获监听器。
     *
     * @return 是否注册成功；未安装或不兼容时返回 false（都会记日志），属正常情况
     */
    public static boolean register(Plugin plugin, ProgressService progress, Consumer<ApplyResult> onProgress) {
        if (!supported()) {
            return false;
        }
        try {
            ClassLoader loader = CustomFishingHook.class.getClassLoader();
            Class<?> listenerClass = Class.forName(LISTENER_CLASS, true, loader);
            Listener listener = (Listener) listenerClass
                    .getConstructor(ProgressService.class, Consumer.class)
                    .newInstance(progress, onProgress);
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            log(Level.INFO, "已接入 CustomFishing " + SoftDependency.versionOf(PLUGIN)
                    + "：任务目标「自定义钓鱼」可用");
            return true;
        } catch (Throwable e) {
            // 版本不兼容（事件类被改名等）时只损失这一个目标类型，不影响插件启动
            log(Level.WARNING, "接入 CustomFishing 失败，「自定义钓鱼」目标将不可用: " + e);
            return false;
        }
    }

    private static void log(Level level, String message) {
        try {
            if (Bukkit.getLogger() != null) {
                Bukkit.getLogger().log(level, "[PlayerTaskX] " + message);
            }
        } catch (Throwable ignored) {
            // 忽略：日志不可用不该影响启动
        }
    }
}
