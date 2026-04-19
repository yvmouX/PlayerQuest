package com.playerPlugin.playerTaskX.storage;

import com.playerPlugin.playerTaskX.api.Enum.PTXStorgeType;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;
import com.playerPlugin.playerTaskX.configuration.StorgeConfiguration;
import com.playerPlugin.playerTaskX.exception.InvalidTaskType;
import com.playerPlugin.playerTaskX.storage.mysql.MySQLProgressStorage;
import com.playerPlugin.playerTaskX.storage.mysql.MySQLSessionStorage;
import com.playerPlugin.playerTaskX.storage.mysql.MySQLTaskStorage;
import com.playerPlugin.playerTaskX.storage.sqlite.SQLiteProgressStorage;
import com.playerPlugin.playerTaskX.storage.sqlite.SQLiteSessionStorage;
import com.playerPlugin.playerTaskX.storage.sqlite.SQLiteTaskStorage;
import com.playerPlugin.playerTaskX.storage.yaml.YamlProgressStorage;
import com.playerPlugin.playerTaskX.storage.yaml.YamlTaskStorage;

import java.io.File;

public class StorageFactory {
    
    public static TaskStorage createTaskStorage(String type, File dataFolder, StorgeConfiguration config) {
        return switch (PTXStorgeType.valueOf(type)) {
            case YAML -> new YamlTaskStorage(dataFolder);
            case SQLITE -> new SQLiteTaskStorage(dataFolder);
            case MYSQL -> new MySQLTaskStorage(config.getMysqlHost(), config.getMysqlPort(), config.getMysqlDatabase(), config.getMysqlUsername(), config.getMysqlPassword());
            default -> throw new IllegalArgumentException("Unknown storage type: " + type);
        };
    }

    public static ProgressStorage createProgressStorage(String type, File dataFolder, StorgeConfiguration config) {
        return switch (PTXStorgeType.valueOf(type)) {
            case YAML -> new YamlProgressStorage(dataFolder);
            case SQLITE -> new SQLiteProgressStorage(dataFolder);
            case MYSQL -> new MySQLProgressStorage(config.getMysqlHost(), config.getMysqlPort(), config.getMysqlDatabase(), config.getMysqlUsername(), config.getMysqlPassword());
            default -> throw new IllegalArgumentException("Unknown storage type: " + type);
        };
    }

    public static SessionStorage createSessionStorage(String type, File dataFolder, StorgeConfiguration config) {
        return switch (PTXStorgeType.valueOf(type)) {
            case SQLITE -> new SQLiteSessionStorage(new File(dataFolder, "quest_sessions.db"));
            case MYSQL -> new MySQLSessionStorage(config.getMysqlHost(), config.getMysqlPort(), config.getMysqlDatabase(), config.getMysqlUsername(), config.getMysqlPassword());
            default -> throw new IllegalArgumentException("Unknown storage type: " + type);
        };
    }

    private PTXStorgeType VerificationStorageType(String type) {
        PTXStorgeType storageType;
        try {
            storageType = PTXStorgeType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            storageType = PTXStorgeType.JSON;
            throw new InvalidTaskType("Invalid storage type: " + type + ", defaulting to JSON");
        }
        return storageType;
    }
}
