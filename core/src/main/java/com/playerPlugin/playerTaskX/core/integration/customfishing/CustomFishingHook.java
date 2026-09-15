package com.playerPlugin.playerTaskX.core.integration.customfishing;

import com.playerPlugin.playerTaskX.core.engine.ApplyResult;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import com.playerPlugin.playerTaskX.core.integration.SoftDependency;

/** CustomFishing 接入点：确认插件存在后反射创建并注册钓获监听器——监听器引用了它的类型，不能直接加载。 */
public final class CustomFishingHook {

    /** 插件名（软依赖探测与日志用）。 */
    public static final String PLUGIN = "CustomFishing";

    private static final String LISTENER_CLASS =
            "com.playerPlugin.playerTaskX.core.integration.customfishing.CustomFishingListener";

    /** 读取战利品清单的实现类；同样只在确认插件存在后才被加载。 */
    private static final String CATALOG_CLASS =
            "com.playerPlugin.playerTaskX.core.integration.customfishing.CustomFishingCatalog";

    /** 清单读取失败只记一次：校验每次都会问一遍清单，不能每次刷一条 warn。 */
    private static boolean catalogFailed;

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

    /**
     * CustomFishing 已注册的战利品清单，供校验比对「鱼 id」是否真的存在。
     * <p>
     * 未安装、版本不兼容、或注册表还空着时返回空表（校验退化为放行，不报错）——
     * 这只是校验的便利功能，任何意外都不该影响插件运行。
     */
    public static List<FishLoot> loot() {
        if (!supported() || catalogFailed) {
            return List.of();
        }
        try {
            Class<?> catalogClass = Class.forName(CATALOG_CLASS, true, CustomFishingHook.class.getClassLoader());
            Object result = catalogClass.getMethod("loot").invoke(null);
            if (result instanceof List<?> list) {
                List<FishLoot> loot = new ArrayList<>(list.size());
                for (Object item : list) {
                    if (item instanceof FishLoot entry) {
                        loot.add(entry);
                    }
                }
                return loot;
            }
            return List.of();
        } catch (Throwable e) {
            // 只记一次：校验每次都会调用这个出口
            catalogFailed = true;
            log(Level.WARNING, "读取 CustomFishing 战利品清单失败，鱼 id 将无法校验: " + e);
            return List.of();
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
