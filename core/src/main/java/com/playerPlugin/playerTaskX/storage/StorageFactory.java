package com.playerPlugin.playerTaskX.storage;

import com.playerPlugin.playerTaskX.api.Enum.PTXStorgeType;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;
import com.playerPlugin.playerTaskX.configuration.StorgeConfiguration;
import com.playerPlugin.playerTaskX.exception.InvalidTaskType;
import com.playerPlugin.playerTaskX.storage.json.JsonProgressStorage;
import com.playerPlugin.playerTaskX.storage.json.JsonSessionStorage;
import com.playerPlugin.playerTaskX.storage.json.JsonTaskStorage;
import com.playerPlugin.playerTaskX.storage.mysql.MySQLProgressStorage;
import com.playerPlugin.playerTaskX.storage.mysql.MySQLSessionStorage;
import com.playerPlugin.playerTaskX.storage.mysql.MySQLTaskStorage;
import com.playerPlugin.playerTaskX.storage.sqlite.SQLiteProgressStorage;
import com.playerPlugin.playerTaskX.storage.sqlite.SQLiteSessionStorage;
import com.playerPlugin.playerTaskX.storage.sqlite.SQLiteTaskStorage;
import com.playerPlugin.playerTaskX.storage.yaml.YamlProgressStorage;
import com.playerPlugin.playerTaskX.storage.yaml.YamlSessionStorage;
import com.playerPlugin.playerTaskX.storage.yaml.YamlTaskStorage;

import java.io.File;

public class StorageFactory {

    public static TaskStorage createTaskStorage(String type, File dataFolder, StorgeConfiguration config) {
        PTXStorgeType storageType = parseStorageType(type);
        return createTaskStorage(storageType, dataFolder, config);
    }

    public static ProgressStorage createProgressStorage(String type, File dataFolder, StorgeConfiguration config) {
        PTXStorgeType storageType = parseStorageType(type);
        return createProgressStorage(storageType, dataFolder, config);
    }

    public static com.playerPlugin.playerTaskX.api.service.SessionStorage createSessionStorage(String type, File dataFolder, StorgeConfiguration config) {
        PTXStorgeType storageType = parseStorageType(type);
        return createSessionStorage(storageType, dataFolder, config);
    }

    private static PTXStorgeType parseStorageType(String type) {
        try {
            return PTXStorgeType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidTaskType("Invalid storage type: " + type + ", defaulting to JSON");
        }
    }

    private static TaskStorage createTaskStorage(PTXStorgeType type, File dataFolder, StorgeConfiguration config) {
        return switch (type) {
            case YAML -> new YamlTaskStorage(dataFolder);
            case JSON -> new JsonTaskStorage(dataFolder);
            case SQLITE -> new SQLiteTaskStorage(dataFolder);
            case MYSQL -> new MySQLTaskStorage(config.getMysqlHost(), config.getMysqlPort(), config.getMysqlDatabase(), config.getMysqlUsername(), config.getMysqlPassword());
        };
    }

    private static ProgressStorage createProgressStorage(PTXStorgeType type, File dataFolder, StorgeConfiguration config) {
        return switch (type) {
            case YAML -> new YamlProgressStorage(dataFolder);
            case JSON -> new JsonProgressStorage(dataFolder);
            case SQLITE -> new SQLiteProgressStorage(dataFolder);
            case MYSQL -> new MySQLProgressStorage(config.getMysqlHost(), config.getMysqlPort(), config.getMysqlDatabase(), config.getMysqlUsername(), config.getMysqlPassword());
        };
    }

    private static com.playerPlugin.playerTaskX.api.service.SessionStorage createSessionStorage(PTXStorgeType type, File dataFolder, StorgeConfiguration config) {
        return switch (type) {
            case YAML -> new YamlSessionStorage(dataFolder);
            case JSON -> new JsonSessionStorage(dataFolder);
            case SQLITE -> new SQLiteSessionStorage(new File(dataFolder, "quest_sessions.db"));
            case MYSQL -> new MySQLSessionStorage(config.getMysqlHost(), config.getMysqlPort(), config.getMysqlDatabase(), config.getMysqlUsername(), config.getMysqlPassword());
        };
    }
}