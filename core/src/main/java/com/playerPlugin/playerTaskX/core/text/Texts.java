package com.playerPlugin.playerTaskX.core.text;

import cn.yvmou.ylib.message.MessageService;
import cn.yvmou.ylib.text.TextRenderer;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Map;

/**
 * 装配玩家可见文本的小工具：渲染色码、数字去小数尾巴、配置表摊成一行、类型显示名。
 * 集中在一处是为了命令/GUI/进度三处口径不漂移；渲染只做一次，调用方拼好原文再交给这里，不要先渲染再拼接。
 */
public final class Texts {

    private Texts() {
    }

    /** 渲染为 {@code §} 色码，供聊天与 {@code ItemMeta#setDisplayName} 使用。 */
    public static String render(String raw) {
        return TextRenderer.render(raw);
    }

    /** 数字文案：去掉整数的小数尾巴（500.0 → 500），小数保持原样。 */
    public static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /**
     * 把配置表摊成一行 {@code (key=value, ...)}；空表返回空串。
     * <p>
     * 键名保持英文标识符原样：它同时是管理员排查配置时需要的原文，翻成中文反而对不上配置文件。
     */
    public static String properties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("(");
        properties.forEach((key, value) -> {
            if (builder.length() > 1) {
                builder.append(", ");
            }
            builder.append(key).append('=').append(value);
        });
        return builder.append(')').toString();
    }

    /** 类型显示名：优先语言键 {@code <group>.<id>}，缺失时退回 fallback（应传类型的 displayName 而非 id 本身）；{@code receiver} 为 {@code null} 时用全局默认语言。 */
    public static String typeName(MessageService messages, CommandSender receiver,
                                  String group, String id, String fallback) {
        String key = group + "." + id;
        if (messages == null || !messages.has(key)) {
            return fallback;
        }
        String raw = receiver == null ? messages.raw(key) : messages.raw(receiver, key);
        return TextRenderer.strip(raw);
    }

    /** 补全前缀匹配：空前缀表示不过滤（YLib 不会对补全结果再过滤一次）。 */
    public static boolean startsWith(String value, String prefix) {
        return prefix == null || prefix.isEmpty()
                || value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }
}
