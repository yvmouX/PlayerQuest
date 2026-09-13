package com.playerPlugin.playerTaskX.core.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * 软依赖探测：判断某个插件是否已加载、版本是多少。
 *
 * <p>三处细节都是踩出来的，别在这里「简化」：
 * <ul>
 *   <li><b>包住 Throwable</b>：{@code Bukkit.getPluginManager()} 在服务端未初始化时返回
 *       {@code null}（单元测试、引导阶段、插件被早期触碰），直接解引用会抛 NPE。
 *       软依赖探测失败只应表示「不可用」，不该让插件崩掉；</li>
 *   <li><b>不 import 对方任何类</b>：本类只按名字查插件，加载它永远是安全的。</li>
 * </ul>
 */
public final class SoftDependency {

    private SoftDependency() {
    }

    /** 插件是否已加载。 */
    public static boolean isPresent(String pluginName) {
        return plugin(pluginName) != null;
    }

    /**
     * 插件的版本号字符串；插件不在或取不到时返回空串。
     * <p>
     * 版本前缀是判断「支持不支持这个插件」的常见依据（MythicMobs 就是 4.x / 5.x 两套完全
     * 不同的 API），因此这里统一提供。
     */
    public static String versionOf(String pluginName) {
        Plugin plugin = plugin(pluginName);
        if (plugin == null || plugin.getDescription() == null) {
            return "";
        }
        String version = plugin.getDescription().getVersion();
        return version == null ? "" : version;
    }

    /** 版本号是否以给定前缀开头（例如 MythicMobs 的 {@code 5.}）。 */
    public static boolean versionStartsWith(String pluginName, String prefix) {
        return versionOf(pluginName).startsWith(prefix);
    }

    @Nullable
    private static Plugin plugin(String pluginName) {
        if (pluginName == null || pluginName.isBlank()) {
            return null;
        }
        try {
            return Bukkit.getPluginManager() == null
                    ? null
                    : Bukkit.getPluginManager().getPlugin(pluginName);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
