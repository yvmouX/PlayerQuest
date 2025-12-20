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

    public StorgeConfiguration() {
    }

    public StorgeConfiguration(String storageMethod_playerData, String storageMethod_questData, String sqlite_file, String mysql_host, int mysql_port, String mysql_database, String mysql_username, String mysql_password, boolean mysql_useSsl, int mysql_poolSize, int connectionPool_maxPoolSize, int connectionPool_minIdle, int connectionPool_connectionTimeout, int connectionPool_idleTimeout, int connectionPool_maxLifetime, boolean cache_enabled, int cache_ttl) {
        this.storageMethod_playerData = storageMethod_playerData;
        this.storageMethod_questData = storageMethod_questData;
        this.sqlite_file = sqlite_file;
        this.mysql_host = mysql_host;
        this.mysql_port = mysql_port;
        this.mysql_database = mysql_database;
        this.mysql_username = mysql_username;
        this.mysql_password = mysql_password;
        this.mysql_useSsl = mysql_useSsl;
        this.mysql_poolSize = mysql_poolSize;
        this.connectionPool_maxPoolSize = connectionPool_maxPoolSize;
        this.connectionPool_minIdle = connectionPool_minIdle;
        this.connectionPool_connectionTimeout = connectionPool_connectionTimeout;
        this.connectionPool_idleTimeout = connectionPool_idleTimeout;
        this.connectionPool_maxLifetime = connectionPool_maxLifetime;
        this.cache_enabled = cache_enabled;
        this.cache_ttl = cache_ttl;
    }

    /**
     * 获取
     * @return storageMethod_playerData
     */
    public String getStorageMethod_playerData() {
        return storageMethod_playerData;
    }

    /**
     * 设置
     * @param storageMethod_playerData
     */
    public void setStorageMethod_playerData(String storageMethod_playerData) {
        this.storageMethod_playerData = storageMethod_playerData;
    }

    /**
     * 获取
     * @return storageMethod_questData
     */
    public String getStorageMethod_questData() {
        return storageMethod_questData;
    }

    /**
     * 设置
     * @param storageMethod_questData
     */
    public void setStorageMethod_questData(String storageMethod_questData) {
        this.storageMethod_questData = storageMethod_questData;
    }

    /**
     * 获取
     * @return sqlite_file
     */
    public String getSqlite_file() {
        return sqlite_file;
    }

    /**
     * 设置
     * @param sqlite_file
     */
    public void setSqlite_file(String sqlite_file) {
        this.sqlite_file = sqlite_file;
    }

    /**
     * 获取
     * @return mysql_host
     */
    public String getMysql_host() {
        return mysql_host;
    }

    /**
     * 设置
     * @param mysql_host
     */
    public void setMysql_host(String mysql_host) {
        this.mysql_host = mysql_host;
    }

    /**
     * 获取
     * @return mysql_port
     */
    public int getMysql_port() {
        return mysql_port;
    }

    /**
     * 设置
     * @param mysql_port
     */
    public void setMysql_port(int mysql_port) {
        this.mysql_port = mysql_port;
    }

    /**
     * 获取
     * @return mysql_database
     */
    public String getMysql_database() {
        return mysql_database;
    }

    /**
     * 设置
     * @param mysql_database
     */
    public void setMysql_database(String mysql_database) {
        this.mysql_database = mysql_database;
    }

    /**
     * 获取
     * @return mysql_username
     */
    public String getMysql_username() {
        return mysql_username;
    }

    /**
     * 设置
     * @param mysql_username
     */
    public void setMysql_username(String mysql_username) {
        this.mysql_username = mysql_username;
    }

    /**
     * 获取
     * @return mysql_password
     */
    public String getMysql_password() {
        return mysql_password;
    }

    /**
     * 设置
     * @param mysql_password
     */
    public void setMysql_password(String mysql_password) {
        this.mysql_password = mysql_password;
    }

    /**
     * 获取
     * @return mysql_useSsl
     */
    public boolean isMysql_useSsl() {
        return mysql_useSsl;
    }

    /**
     * 设置
     * @param mysql_useSsl
     */
    public void setMysql_useSsl(boolean mysql_useSsl) {
        this.mysql_useSsl = mysql_useSsl;
    }

    /**
     * 获取
     * @return mysql_poolSize
     */
    public int getMysql_poolSize() {
        return mysql_poolSize;
    }

    /**
     * 设置
     * @param mysql_poolSize
     */
    public void setMysql_poolSize(int mysql_poolSize) {
        this.mysql_poolSize = mysql_poolSize;
    }

    /**
     * 获取
     * @return connectionPool_maxPoolSize
     */
    public int getConnectionPool_maxPoolSize() {
        return connectionPool_maxPoolSize;
    }

    /**
     * 设置
     * @param connectionPool_maxPoolSize
     */
    public void setConnectionPool_maxPoolSize(int connectionPool_maxPoolSize) {
        this.connectionPool_maxPoolSize = connectionPool_maxPoolSize;
    }

    /**
     * 获取
     * @return connectionPool_minIdle
     */
    public int getConnectionPool_minIdle() {
        return connectionPool_minIdle;
    }

    /**
     * 设置
     * @param connectionPool_minIdle
     */
    public void setConnectionPool_minIdle(int connectionPool_minIdle) {
        this.connectionPool_minIdle = connectionPool_minIdle;
    }

    /**
     * 获取
     * @return connectionPool_connectionTimeout
     */
    public int getConnectionPool_connectionTimeout() {
        return connectionPool_connectionTimeout;
    }

    /**
     * 设置
     * @param connectionPool_connectionTimeout
     */
    public void setConnectionPool_connectionTimeout(int connectionPool_connectionTimeout) {
        this.connectionPool_connectionTimeout = connectionPool_connectionTimeout;
    }

    /**
     * 获取
     * @return connectionPool_idleTimeout
     */
    public int getConnectionPool_idleTimeout() {
        return connectionPool_idleTimeout;
    }

    /**
     * 设置
     * @param connectionPool_idleTimeout
     */
    public void setConnectionPool_idleTimeout(int connectionPool_idleTimeout) {
        this.connectionPool_idleTimeout = connectionPool_idleTimeout;
    }

    /**
     * 获取
     * @return connectionPool_maxLifetime
     */
    public int getConnectionPool_maxLifetime() {
        return connectionPool_maxLifetime;
    }

    /**
     * 设置
     * @param connectionPool_maxLifetime
     */
    public void setConnectionPool_maxLifetime(int connectionPool_maxLifetime) {
        this.connectionPool_maxLifetime = connectionPool_maxLifetime;
    }

    /**
     * 获取
     * @return cache_enabled
     */
    public boolean isCache_enabled() {
        return cache_enabled;
    }

    /**
     * 设置
     * @param cache_enabled
     */
    public void setCache_enabled(boolean cache_enabled) {
        this.cache_enabled = cache_enabled;
    }

    /**
     * 获取
     * @return cache_ttl
     */
    public int getCache_ttl() {
        return cache_ttl;
    }

    /**
     * 设置
     * @param cache_ttl
     */
    public void setCache_ttl(int cache_ttl) {
        this.cache_ttl = cache_ttl;
    }

    public String toString() {
        return "StorgeConfiguration{storageMethod_playerData = " + storageMethod_playerData + ", storageMethod_questData = " + storageMethod_questData + ", sqlite_file = " + sqlite_file + ", mysql_host = " + mysql_host + ", mysql_port = " + mysql_port + ", mysql_database = " + mysql_database + ", mysql_username = " + mysql_username + ", mysql_password = " + mysql_password + ", mysql_useSsl = " + mysql_useSsl + ", mysql_poolSize = " + mysql_poolSize + ", connectionPool_maxPoolSize = " + connectionPool_maxPoolSize + ", connectionPool_minIdle = " + connectionPool_minIdle + ", connectionPool_connectionTimeout = " + connectionPool_connectionTimeout + ", connectionPool_idleTimeout = " + connectionPool_idleTimeout + ", connectionPool_maxLifetime = " + connectionPool_maxLifetime + ", cache_enabled = " + cache_enabled + ", cache_ttl = " + cache_ttl + "}";
    }
}
