package com.playerPlugin.playerTaskX.core.placeholder;

import com.playerPlugin.playerTaskX.core.integration.SoftDependency;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * PlaceholderAPI 反射接入点：把对 PAPI 的编译期依赖关在这一个类里。
 * 必须完全反射——本类不 import 任何 PAPI 类型，也不 import 扩展类本身，否则未装 PlaceholderAPI 的服务端加载时会抛 {@code NoClassDefFoundError}。
 */
public final class PlaceholderHook {

    private static final String PLUGIN = "PlaceholderAPI";

    private static final String EXPANSION_CLASS =
            "com.playerPlugin.playerTaskX.core.placeholder.QuestPlaceholderExpansion";

    private PlaceholderHook() {
    }

    /**
     * 尝试注册变量扩展。
     *
     * @return 是否注册成功（未安装 PlaceholderAPI 时返回 false，属正常情况）
     */
    public static boolean register(Plugin plugin) {
        // 探测统一走 SoftDependency：它已经把「PluginManager 还没起来」这类引导期异常兜住了
        if (!SoftDependency.isPresent(PLUGIN)) {
            return false;
        }
        try {
            ClassLoader loader = PlaceholderHook.class.getClassLoader();
            Class<?> expansionClass = Class.forName(EXPANSION_CLASS, true, loader);
            // 用 Plugin 形参而不是 PlayerTaskX：避免本类引用主类类型，
            // 也让这里不依赖主类的具体类型（构造器由扩展类自行声明）
            Object expansion = expansionClass
                    .getConstructor(Class.forName("org.bukkit.plugin.Plugin", true, loader))
                    .newInstance(plugin);
            Method register = expansionClass.getMethod("register");
            Object result = register.invoke(expansion);
            boolean registered = result instanceof Boolean bool && bool;
            if (registered) {
                plugin.getLogger().info("已注册 PlaceholderAPI 变量（标识符 playertaskx）");
            }
            return registered;
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("注册 PlaceholderAPI 变量失败: " + e.getMessage());
            return false;
        }
    }
}
