package com.playerPlugin.playerTaskX.core.storage.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * JDBC 参数绑定工具。
 */
final class Sql {

    private Sql() {
    }

    /**
     * 绑定参数。
     * <p>
     * 数值与字符串交给 {@link PreparedStatement#setObject} 自己按运行时类型映射
     * （JDBC 规范会转成对应的 {@code java.sql.Types}），省掉一份会随调用点增长而失配的
     * 逐类型分派表。
     * <p>
     * 三处刻意<b>不</b>走 {@code setObject}：
     * <ul>
     *   <li>{@code null}：部分驱动报「无法推断类型」，写明 {@link Types#NULL} 才稳定；</li>
     *   <li>{@code Boolean}：驱动对它的支持参差（本项目也从不传布尔——列都是 SMALLINT）；</li>
     *   <li>其它类型：与旧实现一致地按字符串写入，避免驱动各自发挥。</li>
     * </ul>
     */
    static void bind(PreparedStatement statement, Object... params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            int index = i + 1;
            if (value == null) {
                statement.setNull(index, Types.NULL);
            } else if (value instanceof Integer || value instanceof Long
                    || value instanceof Double || value instanceof Float
                    || value instanceof String) {
                statement.setObject(index, value);
            } else if (value instanceof Boolean bool) {
                statement.setBoolean(index, bool);
            } else {
                statement.setString(index, String.valueOf(value));
            }
        }
    }

    /** 校验内联语句：禁止分号，避免多语句注入。 */
    static void ensureSingleStatement(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        String withoutTrailing = sql.trim();
        while (withoutTrailing.endsWith(";")) {
            withoutTrailing = withoutTrailing.substring(0, withoutTrailing.length() - 1).trim();
        }
        if (withoutTrailing.contains(";")) {
            throw new IllegalArgumentException("不支持一次执行多条语句: " + sql);
        }
    }
}
