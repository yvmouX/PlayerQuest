package com.playerPlugin.playerTaskX;

import cn.yvmou.ylib.YLib;
import cn.yvmou.ylib.api.logger.Logger;
import com.playerPlugin.playerTaskX.api.Enum.PTXStorgeType;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;
import com.playerPlugin.playerTaskX.command.TaskCommand;
import com.playerPlugin.playerTaskX.command.TaskAdminCommand;
import com.playerPlugin.playerTaskX.event.PlayerJoinHandler;
import com.playerPlugin.playerTaskX.listener.EntityListener;
import com.playerPlugin.playerTaskX.listener.BlockListener;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import com.playerPlugin.playerTaskX.manager.RewardManager;
import com.playerPlugin.playerTaskX.storage.StorageFactory;
import com.playerPlugin.playerTaskX.web.EditorServer;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerTaskX extends JavaPlugin {
    private TaskManager taskManager;
    private RewardManager rewardManager;
    private EditorServer editorServer;
    private Logger log;

    @Override
    public void onEnable() {
        YLib ylib = new YLib(this);
        log = ylib.getLogger();

        saveDefaultConfig();
        String storageTypeStr = getConfig().getString("storage.type", "SQLITE");
        PTXStorgeType storageType;
        try {
            storageType = PTXStorgeType.valueOf(storageTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid storage type: " + storageTypeStr + ", defaulting to SQLITE");
            storageType = PTXStorgeType.SQLITE;
        }

        TaskStorage taskStorage;
        ProgressStorage progressStorage;
        if (storageType == PTXStorgeType.MYSQL) {
            StorageFactory.MySQLConfig mysqlConfig = new StorageFactory.MySQLConfig();
            mysqlConfig.host = getConfig().getString("storage.mysql.host", "localhost");
            mysqlConfig.port = getConfig().getInt("storage.mysql.port", 3306);
            mysqlConfig.database = getConfig().getString("storage.mysql.database", "playertaskx");
            mysqlConfig.username = getConfig().getString("storage.mysql.username", "root");
            mysqlConfig.password = getConfig().getString("storage.mysql.password", "");
            taskStorage = StorageFactory.createTaskStorage(storageType, getDataFolder(), mysqlConfig);
            progressStorage = StorageFactory.createProgressStorage(storageType, getDataFolder(), mysqlConfig);
        } else {
            taskStorage = StorageFactory.createTaskStorage(storageType, getDataFolder(), null);
            progressStorage = StorageFactory.createProgressStorage(storageType, getDataFolder(), null);
        }

        this.taskManager = new TaskManager(taskStorage, progressStorage);
        this.rewardManager = new RewardManager();
        taskManager.loadTasks();

        ylib.getCommandManager().register(new TaskCommand(taskManager));
        ylib.getCommandManager().register(new TaskAdminCommand(taskManager));

        getServer().getPluginManager().registerEvents(new EntityListener(taskManager), this);
        getServer().getPluginManager().registerEvents(new BlockListener(taskManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinHandler(taskManager), this);

        int editorPort = getConfig().getInt("editor.port", 8080);
        this.editorServer = new EditorServer(taskManager, getDataFolder().toPath());
        editorServer.start(editorPort);

        log.info("PlayerTaskX enabled - Storage: " + storageType);
    }

    @Override
    public void onDisable() {
        if (editorServer != null) {
            editorServer.stop();
        }
        log.info("PlayerTaskX disabled");
    }
}
