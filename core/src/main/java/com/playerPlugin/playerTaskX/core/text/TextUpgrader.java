package com.playerPlugin.playerTaskX.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * 文本降级工具：把本插件的 Component 转成服务端能直接使用的传统 {@code §} 字符串。
 *
 * <h2>为什么是「降级」而不是「升级」</h2>
 * 插件自带并 relocate 了 Adventure（Spigot 没有这套 API），而 Paper/Folia 服务端内部用的是
 * <b>它自己的</b> {@code net.kyori.adventure} 包——同名不同类，因此我们的 Component
 * 无法直接传给服务端的 {@code sendActionBar(Component)}。
 * <p>
 * 实测还发现：Paper 的 {@code sendActionBar(String)} <b>不解析 MiniMessage</b>，
 * 传 MiniMessage 字符串会把 {@code <yellow>} 这类标签原样显示给玩家。
 * 因此这里统一输出传统 {@code §} 色码——这是 {@code sendActionBar(String)} 这类
 * 旧式字符串接口的通行输入格式，在 Spigot / Paper / Folia 上表现一致。
 */
public final class TextUpgrader {

    private TextUpgrader() {
    }

    /** 把 Component 序列化为传统 {@code §} 色码字符串。 */
    public static String toLegacy(Component component) {
        if (component == null) {
            return "";
        }
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    /** 直接把原始文本（MiniMessage 或 {@code &} 码）转成传统 {@code §} 色码字符串。 */
    public static String toLegacy(String rawText) {
        return toLegacy(TextRenderer.parse(rawText));
    }

    /** 去掉全部格式，仅保留纯文本（用于日志、变量、物品名匹配等）。 */
    public static String toPlain(Component component) {
        if (component == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
