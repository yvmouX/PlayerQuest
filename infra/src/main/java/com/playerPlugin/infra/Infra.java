package com.playerPlugin.infra;

import com.playerPlugin.common.Common;
import com.playerPlugin.core.repository.RepositoryCreator;
import com.playerPlugin.infra.storage.json.JsonRepositoryCreator;
import com.playerPlugin.infra.storage.mysql.MySQLRepositoryCreator;
import com.playerPlugin.infra.storage.sqlite.SQLiteRepositoryCreator;
import com.playerPlugin.infra.storage.yaml.YamlRepositoryCreator;

public class Infra {
    /**
     * 获取存储创建器
     *
     * @return {@link RepositoryCreator }
     */
    public RepositoryCreator getStorageCreator() {
        return switch (Common.DEFAULT_STORAGE) {
            case SQLITE -> new SQLiteRepositoryCreator();
            case MYSQL -> new MySQLRepositoryCreator();
            case JSON -> new JsonRepositoryCreator();
            case YAML -> new YamlRepositoryCreator();
        };
    }
}
