package com.playerPlugin.core.storage;

import cn.yvmou.ylib.tools.LoggerTools;
import com.playerPlugin.core.PlayerTaskX;
import com.playerPlugin.core.common.Common;
import com.playerPlugin.core.service.TaskProgressRepository;
import com.playerPlugin.core.storage.json.JsonRepositoryCreator;
import com.playerPlugin.core.storage.json.JsonTaskProgressRepository;
import com.playerPlugin.core.storage.json.JsonTaskRepository;
import com.playerPlugin.core.storage.mysql.MySQLRepositoryCreator;
import com.playerPlugin.core.storage.mysql.MySQLTaskProgressRepository;
import com.playerPlugin.core.storage.mysql.MySQLTaskRepository;
import com.playerPlugin.core.storage.sqlite.SQLitePlayerProgressRepository;
import com.playerPlugin.core.storage.sqlite.SQLiteRepositoryCreator;
import com.playerPlugin.core.storage.sqlite.SQLiteTaskRepository;
import com.playerPlugin.core.storage.yaml.YamlRepositoryCreator;
import com.playerPlugin.core.storage.yaml.YamlTaskProgressRepository;
import com.playerPlugin.core.storage.yaml.YamlTaskRepository;

public class StorageFactory {
    private final PlayerTaskX plugin;
    private final LoggerTools log;

    public StorageFactory(PlayerTaskX plugin, LoggerTools log) {
        this.plugin = plugin;
        this.log = log;
    }

    public RepositoryCreator getStorageCreator() {
        return switch (Common.DEFAULT_STORAGE) {
            case SQLITE -> new SQLiteRepositoryCreator();
            case MYSQL -> new MySQLRepositoryCreator();
            case JSON -> new JsonRepositoryCreator();
            case YAML -> new YamlRepositoryCreator();
        };
    }
    public TaskProgressRepository getProgressRepository() {
        return switch (Common.DEFAULT_STORAGE) {
            case SQLITE -> new SQLitePlayerProgressRepository();
            case MYSQL -> new MySQLTaskProgressRepository();
            case JSON -> new JsonTaskProgressRepository();
            case YAML -> new YamlTaskProgressRepository(plugin, log);
        };
    }

    public TaskRepository getRepository() {
        return switch (Common.DEFAULT_STORAGE) {
            case SQLITE -> new SQLiteTaskRepository();
            case MYSQL -> new MySQLTaskRepository();
            case JSON -> new JsonTaskRepository();
            case YAML -> new YamlTaskRepository(plugin, log);
        };
    }
}
