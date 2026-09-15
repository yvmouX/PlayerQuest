package com.playerPlugin.playerTaskX.core.storage;

import java.sql.ResultSet;
import java.sql.SQLException;

/** 把 {@link ResultSet} 当前行映射为对象。 */
@FunctionalInterface
public interface RowMapper<T> {

    T map(ResultSet rs) throws SQLException;
}
