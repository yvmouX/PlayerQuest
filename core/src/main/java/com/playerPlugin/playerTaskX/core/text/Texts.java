package com.playerPlugin.playerTaskX.core.text;

import cn.yvmou.ylib.message.MessageService;
import cn.yvmou.ylib.text.TextRenderer;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Map;

/**
 * 装配玩家可见文本的小工具。
 *
 * <h2>为什么集中在这里</h2>
 * 「渲染成 § 色码」「数字去掉小数尾巴」「配置表摊成一行」「类型显示名优先取语言键」
 * 这四件事命令、GUI、进度展示三处都要做。分散实现的代价不是多敲几行，而是
 * <b>三处口径会漂移</b>：一个地方显示「500」，另一个地方显示「500.0」。
 *
 * <h2>渲染只做一次</h2>
 * 所有出口都交给 {@link TextRenderer#render}，它把 {@code &} / {@code §} / MiniMessage
 * 三种写法归一化后一次解析。调用方拼接好<b>原文</b>再交给这里，不要先渲染再拼接。
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

    /**
     * 类型显示名：优先语言键 {@code <group>.<id>}（用户可自定义措辞），
     * 缺失时退回类型自带的显示名。
     * <p>
     * 绝不把 {@code break_block} 这种内部标识抛给玩家——调用方传入的 fallback
     * 应当是类型的 {@code displayName()}，而不是 id 本身。
     *
     * @param receiver 解析语言用的接收者；为 {@code null} 时用全局默认语言（进度展示没有接收者）
     */
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
