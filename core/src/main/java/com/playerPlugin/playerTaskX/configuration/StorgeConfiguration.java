package com.playerPlugin.playerTaskX.configuration;

import cn.yvmou.ylib.api.config.AutoConfiguration;
import cn.yvmou.ylib.api.config.ConfigValue;

@AutoConfiguration(configFile = "storge.yml", version = "1.0.0")
public class StorgeConfiguration {

    @ConfigValue(value = "storage-type", description = "Storage type for plugin data")
    private String storageType = "SQLITE";

    @ConfigValue(value = "mysql.host", description = "MySQL host address")
    private String mysqlHost = "localhost";

    @ConfigValue(value = "mysql.port", description = "MySQL port number")
    private int mysqlPort = 3306;

    @ConfigValue(value = "mysql.database", description = "MySQL database name")
    private String mysqlDatabase = "quest";

    @ConfigValue(value = "mysql.username", description = "MySQL username")
    private String mysqlUsername = "root";

    @ConfigValue(value = "mysql.password", description = "MySQL password")
    private String mysqlPassword = "";

    @ConfigValue(value = "sqlite.file", description = "SQLite database file path")
    private String sqliteFile = "data.db";

    @ConfigValue(value = "connection-pool.max-pool-size", description = "Max pool size for connection pool")
    private int connectionPoolMaxPoolSize = 20;

    @ConfigValue(value = "connection-pool.min-idle", description = "Min idle connections for connection pool")
    private int connectionPoolMinIdle = 5;

    @ConfigValue(value = "connection-pool.connection-timeout", description = "Connection timeout in milliseconds")
    private int connectionPoolConnectionTimeout = 30000;

    @ConfigValue(value = "connection-pool.idle-timeout", description = "Idle timeout in milliseconds")
    private int connectionPoolIdleTimeout = 600000;

    @ConfigValue(value = "connection-pool.max-lifetime", description = "Max lifetime in milliseconds")
    private int connectionPoolMaxLifetime = 1800000;

    @ConfigValue(value = "cache.enabled", description = "Enable cache")
    private boolean cacheEnabled = true;

    @ConfigValue(value = "cache.ttl", description = "Cache TTL in seconds")
    private int cacheTtl = 300;

    public StorgeConfiguration() {
    }

    public String getStorageType() {
        return storageType;
    }

    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public String getSqliteFile() {
        return sqliteFile;
    }

    public int getConnectionPoolMaxPoolSize() {
        return connectionPoolMaxPoolSize;
    }

    public int getConnectionPoolMinIdle() {
        return connectionPoolMinIdle;
    }

    public int getConnectionPoolConnectionTimeout() {
        return connectionPoolConnectionTimeout;
    }

    public int getConnectionPoolIdleTimeout() {
        return connectionPoolIdleTimeout;
    }

    public int getConnectionPoolMaxLifetime() {
        return connectionPoolMaxLifetime;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public int getCacheTtl() {
        return cacheTtl;
    }
}
