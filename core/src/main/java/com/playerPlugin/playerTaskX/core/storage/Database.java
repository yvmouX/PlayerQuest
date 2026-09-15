package com.playerPlugin.playerTaskX.core.storage;

import java.util.List;

/** 极简数据库门面：仓储只依赖这一套方法，避免各写一套 JDBC 样板；参数一律走占位符，无法参数化的 DDL 用 {@link #executeInline}。 */
public interface Database extends AutoCloseable {

    /** 执行写语句（INSERT / UPDATE / DELETE / DDL）。 */
    void execute(String sql, Object... params);

    /**
     * 执行无法使用参数占位符的语句（如含 {@code DEFAULT CHARSET} 的建表语句）。
     * <p>
     * 实现会拒绝包含分号的语句，防止多语句注入。
     */
    void executeInline(String sql);

    /** 查询并映射结果集。 */
    <T> List<T> query(String sql, RowMapper<T> mapper, Object... params);

    /** 查询首行，无结果返回 null。 */
    default <T> T queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = query(sql, mapper, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 查询单列计数，无结果返回 0。 */
    default long count(String sql, Object... params) {
        Long value = queryOne(sql, rs -> rs.getLong(1), params);
        return value == null ? 0L : value;
    }

    /** 在事务中执行，抛异常时回滚。 */
    void transaction(Runnable work);

    /** 当前方言。 */
    Dialect dialect();
}
