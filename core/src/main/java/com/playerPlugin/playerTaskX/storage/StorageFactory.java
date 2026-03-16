package com.playerPlugin.playerTaskX.storage;

import cn.yvmou.ylib.api.logger.Logger;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.Enum.PTXStorgeType;
import com.playerPlugin.playerTaskX.api.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.api.storage.TaskRepository;

public class StorageFactory {
    private final PlayerTaskX plugin;
    private final Logger log;
    private final PTXStorgeType storgeType;

    public StorageFactory(PlayerTaskX plugin, Logger log, PTXStorgeType storgeType) {
        this.plugin = plugin;
        this.log = log;
        this.storgeType = storgeType;
    }

    public TaskProgressRepository getProgressRepository() {
        return switch (storgeType) {
            case SQLITE -> new SqliteTaskProgressRepository(plugin, log);
            //case MYSQL -> new MySQLTaskProgressRepository(plugin, log);
            case YAML -> {
                log.warn("YAML storage for player progress is deprecated. Using SQLite instead.");
                yield new SqliteTaskProgressRepository(plugin, log);
            }
            default -> throw new IllegalArgumentException("Invalid storage type: " + storgeType);
        };
    }

    public TaskRepository getRepository() {
        // Task definitions are always stored in YAML for now
        return new YamlTaskRepository(plugin, log);
    }
}
