/**
 * 存储层：SQLite（开箱即用）与 MySQL 共用一套仓储代码，方言差异集中在 {@link com.playerPlugin.playerTaskX.core.storage.jdbc.Dialect}。
 *
 * <h2>类层次</h2>
 * <ul>
 *   <li><b>根包 = 契约与装配入口</b>：{@link com.playerPlugin.playerTaskX.core.storage.Database}、
 *       {@link com.playerPlugin.playerTaskX.core.storage.QuestRepository}、
 *       {@link com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository}、
 *       {@link com.playerPlugin.playerTaskX.core.storage.StorageException}；
 *       {@link com.playerPlugin.playerTaskX.core.storage.Dialect} 与
 *       {@link com.playerPlugin.playerTaskX.core.storage.RowMapper} 虽是实现味很浓的名字，
 *       但它们出现在 {@code Database} 的方法签名上，属于契约的一部分，因此留在根包；
 *       以及唯一的装配点 {@link com.playerPlugin.playerTaskX.core.storage.DatabaseFactory}。
 *       包外代码只需要 import 这些。</li>
 *   <li><b>{@code jdbc} 子包 = 实现细节</b>：JDBC 通用逻辑、两种连接策略、建表、
 *       三个仓储实现与编解码工具。包外不得 import 其中的类
 *       （{@code DailyService} 对 DailyState 的读取是唯一例外，见其 javadoc）。</li>
 * </ul>
 *
 * <h2>约定</h2>
 * <ul>
 *   <li>SQL 字符串只允许出现在本包（含子包）内，且列名一律经方言转义；</li>
 *   <li>未知存储类型回退 SQLite 而不是启动失败——写错一个单词不该让插件起不来；</li>
 *   <li>不要在服务端运行期间用外部工具改 SQLite 文件：WAL 会回滚外部连接的 DDL，
 *       得出完全错误的结论（踩过）。</li>
 * </ul>
 */
package com.playerPlugin.playerTaskX.core.storage;
