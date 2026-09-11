/**
 * JDBC 实现细节：包外不应 import 此处的类（契约与装配入口在父包）。
 *
 * <h2>连接策略是刻意的两种</h2>
 * SQLite 写入全局串行，用连接池反而制造 SQLITE_BUSY，因此 {@link SqliteDatabase}
 * 只保留一个长驻连接并用 WAL 提升并发读；MySQL 走 {@link MysqlDatabase} 的
 * HikariCP 池化，连接用完必须归还。两种策略都实现 {@link JdbcDatabase.ConnectionProvider}，
 * {@link JdbcDatabase} 只写「连接来源之外」的通用逻辑，不做「猜」。
 *
 * <h2>方言</h2>
 * {@link com.playerPlugin.playerTaskX.core.storage.Dialect}（根包，契约的一部分——{@code Database.dialect()} 暴露它）集中消化语法差异（upsert、标识符转义）；{@link Schema} 是建表
 * DDL 的唯一来源，所有语句经方言生成，同一份定义在两种库上都成立。
 *
 * <h2>容错</h2>
 * {@link JsonCodec} 对库里脏数据一律降级为空集合而不是抛异常——存储层的
 * 容错优先级高于严格性；{@link Sql} 收敛 JDBC 参数绑定样板。
 */
package com.playerPlugin.playerTaskX.core.storage.jdbc;

