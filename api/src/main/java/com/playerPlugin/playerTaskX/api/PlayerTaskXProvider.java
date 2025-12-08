package com.playerPlugin.playerTaskX.api;

/**
 * API 提供者
 * 用于获取 PlayerTaskXAPI 实例
 */
public final class PlayerTaskXProvider {
    private static PlayerTaskXAPI api;

    private PlayerTaskXProvider() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 设置 API 实例（仅供插件内部使用）
     * @param instance API 实例
     */
    public static void setApi(PlayerTaskXAPI instance) {
        if (api != null) {
            throw new IllegalStateException("API instance already set");
        }
        api = instance;
    }

    /**
     * 获取 API 实例
     * @return API 实例
     * @throws IllegalStateException 如果 API 未初始化
     */
    public static PlayerTaskXAPI getApi() {
        if (api == null) {
            throw new IllegalStateException("PlayerTaskX API is not initialized. Make sure PlayerTaskX plugin is loaded.");
        }
        return api;
    }

    /**
     * 检查 API 是否已加载
     * @return 是否已加载
     */
    public static boolean isLoaded() {
        return api != null;
    }

    /**
     * 重置 API 实例（仅供插件内部使用）
     */
    public static void reset() {
        api = null;
    }
}
