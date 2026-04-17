package com.playerPlugin.playerTaskX.storage;

import com.playerPlugin.playerTaskX.api.Enum.PTXStorgeType;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;
import com.playerPlugin.playerTaskX.storage.yaml.*;
import com.playerPlugin.playerTaskX.storage.sqlite.*;
import com.playerPlugin.playerTaskX.storage.mysql.*;

import java.io.File;

public class StorageFactory {
    
    public static TaskStorage createTaskStorage(PTXStorgeType type, File dataFolder, MySQLConfig mysqlConfig) {
        return switch (type) {
            case YAML -> new YamlTaskStorage(dataFolder);
            case SQLITE -> new SQLiteTaskStorage(dataFolder);
            case MYSQL -> new MySQLTaskStorage(mysqlConfig.host, mysqlConfig.port, mysqlConfig.database, mysqlConfig.username, mysqlConfig.password);
            default -> throw new IllegalArgumentException("Unknown storage type: " + type);
        };
    }

    public static ProgressStorage createProgressStorage(PTXStorgeType type, File dataFolder, MySQLConfig mysqlConfig) {
        return switch (type) {
            case YAML -> new YamlProgressStorage(dataFolder);
            case SQLITE -> new SQLiteProgressStorage(dataFolder);
            case MYSQL -> new MySQLProgressStorage(mysqlConfig.host, mysqlConfig.port, mysqlConfig.database, mysqlConfig.username, mysqlConfig.password);
            default -> throw new IllegalArgumentException("Unknown storage type: " + type);
        };
    }

    public static class MySQLConfig {
        public String host;
        public int port;
        public String database;
        public String username;
        public String password;
    }
}
