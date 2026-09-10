package com.playerPlugin.playerTaskX.core.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * JDBC 参数绑定工具。
 */
final class Sql {

    private Sql() {
    }

    static void bind(PreparedStatement statement, Object... params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            int index = i + 1;
            if (value == null) {
                statement.setNull(index, Types.NULL);
            } else if (value instanceof Integer number) {
                statement.setInt(index, number);
            } else if (value instanceof Long number) {
                statement.setLong(index, number);
            } else if (value instanceof Double number) {
                statement.setDouble(index, number);
            } else if (value instanceof Float number) {
                statement.setFloat(index, number);
            } else if (value instanceof Boolean bool) {
                statement.setBoolean(index, bool);
            } else {
                statement.setString(index, String.valueOf(value));
            }
        }
    }

    /** 生成 {@code (?, ?, ...)}，用于 upsert 的 VALUES 段。 */
    static String placeholders(int count) {
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < count; i++) {
            if (i > 0) builder.append(", ");
            builder.append('?');
        }
        return builder.append(')').toString();
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
