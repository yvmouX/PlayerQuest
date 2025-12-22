package com.playerPlugin.playerTaskX.storage;

import cn.yvmou.ylib.api.services.LoggerService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.Enum.PTXStorgeType;
import com.playerPlugin.playerTaskX.api.storage.RepositoryCreator;
import com.playerPlugin.playerTaskX.api.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.api.storage.TaskRepository;
import com.playerPlugin.playerTaskX.storage.yaml.YamlRepositoryCreator;
import com.playerPlugin.playerTaskX.storage.yaml.YamlTaskProgressRepository;
import com.playerPlugin.playerTaskX.storage.yaml.YamlTaskRepository;

public class StorageFactory {
    private final PlayerTaskX plugin;
    private final LoggerService log;
    private final PTXStorgeType storgeType;

    public StorageFactory(PlayerTaskX plugin, LoggerService log, PTXStorgeType storgeType) {
        this.plugin = plugin;
        this.log = log;
        this.storgeType = storgeType;
    }

    public RepositoryCreator getStorageCreator() {
        return switch (storgeType) {
//            case SQLITE -> new SQLiteRepositoryCreator();
//            case MYSQL -> new MySQLRepositoryCreator();
//            case JSON -> new JsonRepositoryCreator();
            case YAML -> new YamlRepositoryCreator();
            default -> throw new IllegalArgumentException("Invalid storage type: " + storgeType); // TODO
        };
    }
    public TaskProgressRepository getProgressRepository() {
        return switch (storgeType) {
//            case SQLITE -> new SQLitePlayerProgressRepository();
//            case MYSQL -> new MySQLTaskProgressRepository();
//            case JSON -> new JsonTaskProgressRepository();
            case YAML -> new YamlTaskProgressRepository(plugin, log);
            default -> throw new IllegalArgumentException("Invalid storage type: " + storgeType); // TODO
        };
    }

    public TaskRepository getRepository() {
        return switch (storgeType) {
//            case SQLITE -> new SQLiteTaskRepository();
//            case MYSQL -> new MySQLTaskRepository();
//            case JSON -> new JsonTaskRepository();
            case YAML -> new YamlTaskRepository(plugin, log);
            default -> throw new IllegalArgumentException("Invalid storage type: " + storgeType); // TODO
        };
    }
}
