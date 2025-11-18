package com.playerPlugin.core.dataManager;

import com.playerPlugin.core.domain.PlayerTask.Enum.PTXTaskStatus;
import com.playerPlugin.core.domain.PlayerTask.Task.PlayerTask;
import com.playerPlugin.core.domain.PlayerTask.Task.TaskDefinition;
import com.playerPlugin.core.domain.PlayerTask.Task.TaskTarget;
import com.playerPlugin.core.domain.PlayerTask.TaskManager;
import com.playerPlugin.core.PlayerTaskX;
import com.playerPlugin.core.dataManager.cache.PlayerTaskCache;
import com.playerPlugin.core.dataManager.dao.CacheDAO;
import com.playerPlugin.core.dataManager.dao.DatabaseDAO;
import com.playerPlugin.core.dataManager.impl.SQLiteManager;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static com.playerPlugin.core.utils.Help.*;

public class StorgeManager {
    private final TaskManager tm;
    // dao
    private static CacheDAO cacheDAO;
    private static DatabaseDAO databaseDAO;
    // sqlite
    private final SQLiteManager sqLiteManager;
    // other
    private final PlayerTaskX plugin;
    private final StorgeTypes type;
    private static DataSyncTask dataSyncTask;

    public StorgeManager(PlayerTaskX plugin, StorgeTypes storge, SQLiteManager sqLiteManager, TaskManager tm) {
        this.plugin = plugin;
        this.type = storge;
        this.sqLiteManager = sqLiteManager;
        this.tm = tm;

        // DAO
        cacheDAO = new CacheDAO(this, new PlayerTaskCache());
        databaseDAO = new DatabaseDAO(this);
        // 数据同步任务
        dataSyncTask = new DataSyncTask(plugin, cacheDAO.getPlayerTaskCache(), databaseDAO);
    }

    public CacheDAO getCacheDAO() {
        if (cacheDAO == null) {
            log.error("CacheDAO is not initialized");
        }
        return cacheDAO;
    }

    public DatabaseDAO getDatabaseDAO() {
        if (databaseDAO == null) {
            log.error("DatabaseDAO is not initialized");
        }
        return databaseDAO;
    }

    public SQLiteManager getSQLiteManager() {
        if (sqLiteManager == null) {
            log.error("SQLiteManager is not initialized");
        }
        return sqLiteManager;
    }

    public DataSyncTask getDataSyncTask() {
        if (dataSyncTask == null) {
            log.error("DataSyncTask is not initialized");
        }
        return dataSyncTask;
    }

    /**
     * 连接 / 创建表
     *
     */
    public void connect() {
        switch (type) {
            case SQLITE -> {
                // 数据库
                try {
                    sqLiteManager.connect(plugin);
                    log.info("成功连接到 SQLite 数据库！");
                } catch (SQLException | ClassNotFoundException e) {
                    log.error("连接到 SQLite 数据库失败：" + e.getMessage());
                    log.error("插件已禁用！");
                    plugin.getServer().getPluginManager().disablePlugin(plugin);
                }
            }
            case MYSQL -> {
                // TODO
            }
        }
    }


    public Connection getConnection() {
        switch (type) {
            case SQLITE -> {
                return sqLiteManager.getConnection();
            }
            case MYSQL -> {

            }
        }
        return null;
    }


    public void close() {
        switch (type) {
            case SQLITE -> {
                sqLiteManager.close();
            }
            case MYSQL -> {

            }
        }
    }

    /**
     * 从数据库加载数据到缓存
     *
     */
    public void databaseToCache(Player p) {
        List<PlayerTask> inProgressTaskIdList = new LinkedList<>();
        try {
            inProgressTaskIdList = getTaskListFromDatabase(p.getUniqueId(), PTXTaskStatus.IN_PROGRESS);
        } catch (SQLException e) {
            log.error("从数据库获取玩家 " + p.getName() + " 进行中的任务时失败：" + e.getMessage());
        }

        if (inProgressTaskIdList != null) {
            getCacheDAO().updatePlayerTaskToCache(inProgressTaskIdList, false);
            log.debug("已加载玩家 " + p.getName() + " 进行中的任务：" + inProgressTaskIdList + " 共 " + inProgressTaskIdList.size() + " 个");
        }
    }

    public void cacheToDatabase(Player p) {
        // TODO
    }

    /**
     * 获取指定玩家的进行中任务ID列表
     * 状态: 0:进行中
     *
     * @param uuid           玩家UUID
     * @param requiredStatus 必需状态
     * @return {@link List }<{@link String }>
     * @throws SQLException sql异常
     */
    public List<String> getTaskIdListFromDatabase(UUID uuid, PTXTaskStatus requiredStatus) throws SQLException {
        List<String> result = new ArrayList<>();
        String sql = "SELECT * FROM player_tasks WHERE player_uuid = ? AND status = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, requiredStatus.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                if (isRequiredStatus(requiredStatus, rs)) continue;

                result.add(rs.getString("task_id"));
            }
        }
        return result;
    }

    /**
     * 从数据库获取任务列表
     *
     * @param uuid           uuid
     * @param requiredStatus 必需状态
     * @return {@link List }<{@link PlayerTask }>
     * @throws SQLException sql异常
     */
    public List<PlayerTask> getTaskListFromDatabase(UUID uuid, PTXTaskStatus requiredStatus) throws SQLException {
        List<PlayerTask> result = new ArrayList<>();
        String sql = "SELECT * FROM player_tasks WHERE player_uuid = ? AND status = 0";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                if (isRequiredStatus(requiredStatus, rs)) continue;

                String taskId = rs.getString("task_id");

                // 处理任务目标，设置为玩家当前的进度
                List<TaskTarget> handledTaskTargetList = tm.getTaskTargets(taskId);
                for (TaskTarget taskTarget : handledTaskTargetList) {
                    Map<Integer, Integer> progressMap = getDatabaseDAO().getProgress(uuid.toString(), taskId);
                    taskTarget.setCurrent(
                            progressMap.get(taskTarget.getIndex())
                    );
                }

                result.add(
                        new PlayerTask(uuid,
                                new TaskDefinition
                                        (
                                                taskId,
                                                tm.getTaskType(taskId),
                                                tm.getTaskName(taskId),
                                                handledTaskTargetList,
                                                tm.getTaskTrigger(taskId)
                                        )));
            }
        }
        return result;
    }

    private boolean isRequiredStatus(PTXTaskStatus requiredStatus, ResultSet rs) throws SQLException {
        PTXTaskStatus status = switch (rs.getInt("status")) {
            case 0 -> PTXTaskStatus.IN_PROGRESS;
            case 1 -> PTXTaskStatus.COMPLETED;
            case 2 -> PTXTaskStatus.FAILED;
            default -> throw new IllegalStateException("Unexpected value: " + rs.getInt("status"));
        };

        if (status != requiredStatus) {
            return true;
        }
        return false;
    }
}
