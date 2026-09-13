/**
 * JDBC 实现细节：包外不应 import 此处的类（契约与装配入口在父包）。
 *
 * <h2>连接策略是刻意的两种</h2>
 * SQLite 写入全局串行，用连接池反而制造 SQLITE_BUSY，因此走单连接长驻 + WAL；
 * MySQL 走 HikariCP 池化，连接用完必须归还。两种策略都在
 * {@link com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcDatabase} 的两个工厂里，
 * 执行逻辑只有一份。
 *
 * <h2>方言</h2>
 * {@link com.playerPlugin.playerTaskX.core.storage.Dialect}（根包，契约的一部分——
 * {@code Database.dialect()} 暴露它）集中消化语法差异（upsert、标识符转义）；
 * {@link com.playerPlugin.playerTaskX.core.storage.jdbc.Schema} 是建表 DDL 的唯一来源，
 * 所有语句经方言生成，同一份定义在两种库上都成立。
 *
 * <h2>容错</h2>
 * {@link com.playerPlugin.playerTaskX.core.storage.JsonCodec} 对库里脏数据一律降级为空集合
 * 而不是抛异常——存储层的容错优先级高于严格性；
 * {@link com.playerPlugin.playerTaskX.core.storage.jdbc.Sql} 收敛 JDBC 参数绑定样板。
 */
package com.playerPlugin.playerTaskX.core.storage.jdbc;
