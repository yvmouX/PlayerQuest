package com.playerPlugin.playerTaskX.configuration;

import cn.yvmou.ylib.api.config.AutoConfiguration;
import cn.yvmou.ylib.api.config.ConfigValue;

@SuppressWarnings("unused")
@AutoConfiguration(configFile = "storge.yml", version = "1.0.0")
public class StorgeConfiguration {

    @ConfigValue(value = "storage-type.task-definition", description = "Storage type for task definition data")
    private String StorageType_TaskDefinition = "JSON";

    @ConfigValue(value = "storage-type.player-progress", description = "Storage type for player progress data")
    private String StorageType_PlayerProgress = "JSON";

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

    public StorgeConfiguration() {
    }

    public String getStorageType_TaskDefinition() {
        return StorageType_TaskDefinition;
    }

    public void setStorageType_TaskDefinition(String StorageType_TaskDefinition) {
        this.StorageType_TaskDefinition = StorageType_TaskDefinition;
    }

    public String getStorageType_PlayerProgress() {
        return StorageType_PlayerProgress;
    }

    public void setStorageType_PlayerProgress(String StorageType_PlayerProgress) {
        this.StorageType_PlayerProgress = StorageType_PlayerProgress;
    }

    public String getMysqlHost() {
        return mysqlHost;
    }

    public void setMysqlHost(String mysqlHost) {
        this.mysqlHost = mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public void setMysqlPort(int mysqlPort) {
        this.mysqlPort = mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public void setMysqlDatabase(String mysqlDatabase) {
        this.mysqlDatabase = mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public void setMysqlUsername(String mysqlUsername) {
        this.mysqlUsername = mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public void setMysqlPassword(String mysqlPassword) {
        this.mysqlPassword = mysqlPassword;
    }
}
