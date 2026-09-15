package com.playerPlugin.playerTaskX.core.gui.editor;

import com.playerPlugin.playerTaskX.api.schema.ConfigField;

/**
 * 聊天栏文本 ↔ 字段值的转换：输入解析与回显都不碰 Bukkit，因此能脱离服务端直接测。
 */
public final class FieldValue {

    private FieldValue() {
    }

    /** 解析聊天栏输入：空串 = 清除该字段（返回 null）；非法时抛 IllegalArgumentException，消息可直接给玩家看。 */
    public static Object parse(ConfigField field, String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        return switch (field.shape()) {
            case TEXT, CANDIDATES -> text;
            case INTEGER -> integer(text);
            case DECIMAL -> decimal(text);
            case BOOLEAN -> bool(text);
        };
    }

    /** 配置值 → 给玩家看的短文本。 */
    public static String display(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean flag) {
            return flag ? "是" : "否";
        }
        // 解析出来的一律是 Double，但配置里常见的「1」会被存成整数；
        // 整数值显示成 3.0 只会让管理员怀疑自己填错了
        if (value instanceof Number number && isWhole(number)) {
            return String.valueOf(number.longValue());
        }
        return String.valueOf(value);
    }

    private static Long integer(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            // 原样给玩家看，别让 NumberFormatException 的英文串漏到聊天栏
            throw new IllegalArgumentException("需要一个整数，例如 3");
        }
    }

    private static Double decimal(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("需要一个小数，例如 1.5");
        }
    }

    private static Boolean bool(String text) {
        return switch (text.toLowerCase()) {
            case "true", "yes", "on", "1", "是", "开" -> Boolean.TRUE;
            case "false", "no", "off", "0", "否", "关" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("只能填 是 或 否");
        };
    }

    /** 是否是不带小数部分的有限数值；范围外（超出 long）也判否，免得转型溢出。 */
    private static boolean isWhole(Number number) {
        double value = number.doubleValue();
        return Double.isFinite(value) && value == Math.rint(value)
                && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE;
    }
}
