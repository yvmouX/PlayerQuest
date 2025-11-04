package com.playerPlugin.playerTaskX.dataManager.dao;

import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import com.playerPlugin.playerTaskX.dataManager.impl.SQLiteManager;
import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 玩家任务 DAO
 *
 * @author yvmoux
 * &#064;date  2025/11/03
 */
public class PlayerTaskDAO {
    private final StorgeManager db;

    public PlayerTaskDAO(StorgeManager db) {
        this.db = db;
    }

    /**
     * 开始任务
     * 状态: 0:进行中, 1:完成, 2:失败
     *
     * @param tasks 任务列表
     */
    public void startTask(List<PlayerTask> tasks) throws SQLException {
        String sql = "INSERT OR IGNORE INTO player_tasks (player_uuid, task_id, status, start_time) VALUES (?, ?, 0, ?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (PlayerTask task : tasks) {
                ps.setString(1, task.getUUID().toString());
                ps.setString(2, task.getTask().getId());
                ps.setLong(3, System.currentTimeMillis());
                ps.addBatch();
            }
            int[] a = ps.executeBatch();

            // TODO 调试内容
            if (a.length > 0) {
                PlayerTaskX.getYLib().getLoggerTools().info("成功插入玩家任务数据：" + tasks.size() + "影响行数：" + a);
            } else {
                PlayerTaskX.getYLib().getLoggerTools().info("插入玩家任务数据失败：" + tasks.size() + "影响行数：" + a);
            }
        }
    }

    /**
     * 更新任务列表
     *
     * @param tasks 任务列表
     * @throws SQLException
     */
    public void updateTasks(List<PlayerTask> tasks) throws SQLException {
        String sql = "UPDATE player_tasks SET status = ? WHERE player_uuid = ? AND task_id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (PlayerTask task : tasks) {
                int statusId = switch (task.getStatus()) {
                    case IN_PROGRESS -> 0;
                    case COMPLETED -> 1;
                    case FAILED -> 2;
                };
                ps.setInt(1, statusId);
                ps.setString(2, task.getUUID().toString());
                ps.setString(3, task.getTask().getId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * 完成任务
     *
     * @param uuid   uuid
     * @param taskId 任务 ID
     */
     public void finishTask(String uuid, String taskId) throws SQLException {
        String sql = "UPDATE player_tasks SET status = 1, finish_time = ? WHERE player_uuid = ? AND task_id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, uuid);
            ps.setString(3, taskId);
            ps.executeUpdate();
        }
    }

    /**
     * 获取任务状态
     * 状态: 0:进行中, 1:完成, 2:失败
     *
     * @param uuid   uuid
     * @param taskId 任务 ID
     * @return {@link Optional }<{@link Integer }>
     */
    public Optional<Integer> getTaskStatus(String uuid, String taskId) throws SQLException {
        String sql = "SELECT status FROM player_tasks WHERE player_uuid = ? AND task_id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, taskId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(rs.getInt("status"));
            }
        }
        return Optional.empty();
    }

    /**
     * 获取指定玩家的进行中任务ID列表
     * 状态: 0:进行中
     *
     * @param uuid 玩家UUID
     * @return 进行中任务ID列表
     */
    public List<String> getInProgressTaskIds(String uuid) throws SQLException {
        List<String> result = new ArrayList<>();
        String sql = "SELECT task_id FROM player_tasks WHERE player_uuid = ? AND status = 0";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(rs.getString("task_id"));
            }
        }
        return result;
    }
}
