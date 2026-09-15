package com.playerPlugin.playerTaskX.core.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/** 软依赖探测：按插件名查是否已加载与版本号；未初始化或探测失败一律当作「不可用」，不抛异常。 */
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
