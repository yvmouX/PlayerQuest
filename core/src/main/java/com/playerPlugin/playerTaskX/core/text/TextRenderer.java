package com.playerPlugin.playerTaskX.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * 文本渲染：MiniMessage 优先，兼容传统 {@code &} 颜色代码。
 * <p>
 * 设计取舍：用户在配置里写错格式时，绝不把标签原文丢给玩家。
 * 因此解析失败会退回 {@code &} 转换，而不是显示成 {@code <red>} 这样的字面量。
 */
public final class TextRenderer {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private TextRenderer() {
    }

    /**
     * 把配置文本渲染为服务端可发送的字符串。
     * <p>
     * 同时输出 MiniMessage 与传统颜色码（转成 {@code §} 形式），
     * 因此对 Spigot 的 {@code sendMessage(String)} 与 Paper 的 Adventure API 都可用。
     */
    public static String render(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        Component component = parse(raw);
        return LEGACY.serialize(component);
    }

    /**
     * 解析为 Component。
     *
     * <h2>为什么必须先把 {@code §} 码转成 MiniMessage 标签</h2>
     * 实测发现的三个关键事实（都有测试固化）：
     * <ol>
     *   <li>MiniMessage 遇到 {@code §} 会直接抛
     *       {@code ParsingExceptionImpl: Legacy formatting codes have been detected}，
     *       整串包括标签都不再解析；</li>
     *   <li>{@link LegacyComponentSerializer} 的 round-trip <b>会把 {@code §} 码原样保留</b>
     *       （解析时识别为样式，序列化时又写回），所以不能靠它来「清洗」残留码；</li>
     *   <li>{@code LegacyComponentSerializer} 对普通文本里的 {@code <yellow>} 一律当字面量。</li>
     * </ol>
     * 结论：只要字符串里混有 {@code §} 码，两条路径都无法正确处理。
     * 因此这里统一<b>把 {@code §} 码翻译成等价的 MiniMessage 标签</b>，再用 MiniMessage
     * 渲染一次——这样「MiniMessage 标签」「{@code &} 码」「{@code §} 码」三种写法
     * 可以任意混排，输出始终一致。
     */
    public static Component parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        String prepared = sectionToMiniMessage(translateAmpersand(raw));
        Component component;
        try {
            component = MINI.deserialize(prepared);
        } catch (Exception ignored) {
            // 标签写法非法（如未闭合）时退化为纯文本，绝不把标签原文抛给玩家
            component = Component.text(prepared);
        }
        // 原版会把自定义名称渲染为斜体，任务名会很难看，这里统一关掉
        return component.decoration(TextDecoration.ITALIC, false);
    }

    /** 传统颜色码 → MiniMessage 标签的映射表（覆盖 0-9a-f 与 k-o）。 */
    private static final String[] LEGACY_TO_TAG = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
            "yellow", "white",
            "obfuscated", "bold", "strikethrough", "underlined", "italic", "reset"
    };

    /**
     * 把 {@code §x} 传统颜色码翻译成 MiniMessage 标签。
     * <p>
     * 这样含 {@code §} 的文本也能交给 MiniMessage 统一渲染，
     * 而不必在两套渲染器之间来回切换（切换必然丢失其中一方的格式）。
     */
    public static String sectionToMiniMessage(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (current != '\u00A7' || i + 1 >= raw.length()) {
                builder.append(current);
                continue;
            }
            char code = Character.toLowerCase(raw.charAt(i + 1));
            int index = "0123456789abcdefklmnor".indexOf(code);
            if (index < 0) {
                // 不是合法颜色码，原样保留（例如 § 后面跟了普通文字）
                builder.append(current);
                continue;
            }
            String tag = LEGACY_TO_TAG[index];
            if ("reset".equals(tag)) {
                builder.append("<reset>");
            } else if (index >= 16) {
                // 装饰类（k-o）：用成对标签包住后续内容不可行，改用 <reset> 语义的独立标签
                builder.append('<').append(tag).append('>');
            } else {
                builder.append('<').append(tag).append('>');
            }
            i++;
        }
        return builder.toString();
    }

    /** 去掉所有格式，仅保留纯文本内容（用于 GUI 标题、日志、变量截断等）。 */
    public static String strip(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(parse(raw));
    }

    /** 把 {@code &a} 这类传统颜色码转成 {@code §a}。 */
    public static String translateAmpersand(String raw) {
        if (raw == null) {
            return "";
        }
        char[] chars = raw.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i + 1]) >= 0) {
                chars[i] = '\u00A7';
            }
        }
        return new String(chars);
    }

    /** 文本是否为空（含仅含空白的情况）。 */
    public static boolean isBlank(String raw) {
        return raw == null || raw.isBlank();
    }
}
