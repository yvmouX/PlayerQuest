package com.playerPlugin.playerTaskX.configuration;

import cn.yvmou.ylib.api.config.AutoConfiguration;
import cn.yvmou.ylib.api.config.ConfigValue;

@AutoConfiguration(configFile = "storage.yml")
public class StorgeConfiguration {
    /**
     * storage-method:
  player-data: yaml
  quest-data: yaml
sqlite:
  file: data.db
mysql:
  host: "localhost"
  port: 3306
  database: "quest"
  username: "root"
  password: ""
  use-ssl: false
  pool-size: 10
connection-pool:
  max-pool-size: 20
  min-idle: 5
  connection-timeout: 30000
  idle-timeout: 600000
  max-lifetime: 1800000
cache:
  enabled: true
  ttl: 300

     */
    @ConfigValue(
        value = "storage-method.player-data",
        description = "储存玩家数据的方法。yaml 或 sqlite 或 mysql。"
    )
    private String storageMethod_playerData = "yaml";

    @ConfigValue(
        value = "storage-method.quest-data",
        description = "储存任务数据的方法。yaml 或 sqlite 或 mysql。"
    )
    private String storageMethod_questData = "yaml";

    @ConfigValue(
        value = "sqlite.file",
        description = "SQLite 数据库文件路径。"
    )
    private String sqlite_file = "data.db";

    @ConfigValue(
        value = "mysql.host",
        description = "MySQL 数据库主机地址。"
    )
    private String mysql_host = "localhost";

    @ConfigValue(
        value = "mysql.port",
        description = "MySQL 数据库端口号。"
    )
    private int mysql_port = 3306;

    @ConfigValue(
        value = "mysql.database",
        description = "MySQL 数据库名称。"
    )
    private String mysql_database = "quest";

    @ConfigValue(
        value = "mysql.username",
        description = "MySQL 数据库用户名。"
    )
    private String mysql_username = "root";

    @ConfigValue(
        value = "mysql.password",
        description = "MySQL 数据库密码。"
    )
    private String mysql_password = "";

    @ConfigValue(
        value = "mysql.use-ssl",
        description = "是否使用 SSL 连接 MySQL 数据库。"
    )
    private boolean mysql_useSsl = false;

    @ConfigValue(
        value = "mysql.pool-size",
        description = "MySQL 数据库连接池大小。"
    )
    private int mysql_poolSize = 10;

    @ConfigValue(
        value = "connection-pool.max-pool-size",
        description = "数据库连接池最大连接数。"
    )
    private int connectionPool_maxPoolSize = 20;

    @ConfigValue(
        value = "connection-pool.min-idle",
        description = "数据库连接池最小空闲连接数。"
    )
    private int connectionPool_minIdle = 5;

    @ConfigValue(
        value = "connection-pool.connection-timeout",
        description = "数据库连接超时时间（毫秒）。"
    )
    private int connectionPool_connectionTimeout = 30000;

    @ConfigValue(
        value = "connection-pool.idle-timeout",
        description = "数据库连接池空闲连接超时时间（毫秒）。"
    )
    private int connectionPool_idleTimeout = 600000;

    @ConfigValue(
        value = "connection-pool.max-lifetime",
        description = "数据库连接池连接最大生命周期（毫秒）。"
    )
    private int connectionPool_maxLifetime = 1800000;

    @ConfigValue(
        value = "cache.enabled",
        description = "是否启用缓存。"
    )
    private boolean cache_enabled = true;

    @ConfigValue(
        value = "cache.ttl",
        description = "缓存过期时间（秒）。"
    )
    private int cache_ttl = 300;
}
