package com.playerPlugin.playerTaskX.extension;

/**
 *
 * 扩展在注册时应提供 `extension.properties` 或 `META-INF` 声明其兼容版本，核心在加载时调用 `checkVersion`。
 */
public final class ExtensionCompatibility {
    public static void checkVersion(String pluginApiVersion, String extDeclaredVersion) {
        // 简单规则：主版本号必须一致
        String main1 = pluginApiVersion.split("\.")[0];
        String main2 = extDeclaredVersion.split("\.")[0];
        if (!main1.equals(main2)) throw new IllegalStateException("Incompatible extension API version");
    }
}
