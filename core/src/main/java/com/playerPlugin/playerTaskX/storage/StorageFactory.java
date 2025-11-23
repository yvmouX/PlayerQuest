package com.playerPlugin.playerTaskX.storage;

import cn.yvmou.ylib.tools.LoggerTools;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.common.Common;
import com.playerPlugin.playerTaskX.service.TaskProgressRepository;
import com.playerPlugin.playerTaskX.storage.json.JsonRepositoryCreator;
import com.playerPlugin.playerTaskX.storage.json.JsonTaskProgressRepository;
import com.playerPlugin.playerTaskX.storage.json.JsonTaskRepository;
import com.playerPlugin.playerTaskX.storage.mysql.MySQLRepositoryCreator;
import com.playerPlugin.playerTaskX.storage.mysql.MySQLTaskProgressRepository;
import com.playerPlugin.playerTaskX.storage.mysql.MySQLTaskRepository;
import com.playerPlugin.playerTaskX.storage.sqlite.SQLitePlayerProgressRepository;
import com.playerPlugin.playerTaskX.storage.sqlite.SQLiteRepositoryCreator;
import com.playerPlugin.playerTaskX.storage.sqlite.SQLiteTaskRepository;
import com.playerPlugin.playerTaskX.storage.yaml.YamlRepositoryCreator;
import com.playerPlugin.playerTaskX.storage.yaml.YamlTaskProgressRepository;
import com.playerPlugin.playerTaskX.storage.yaml.YamlTaskRepository;

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
