package com.playerPlugin.playerTaskX.core.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * PlaceholderAPI 反射接入点。
 * <p>
 * 存在的唯一理由：把对 PlaceholderAPI 的编译期依赖关在这一个类里，
 * 而且必须是<b>完全反射</b>——本类不 import 任何 PAPI 类型，
 * 也不 import 那个实现了 PAPI 抽象类的扩展类本身
 * （加载它就会触发 PAPI 类解析）。
 * <p>
 * 否则未安装 PlaceholderAPI 的服务端会在加载本类时抛 {@code NoClassDefFoundError}。
 */
public final class PlaceholderHook {

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
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
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
