package com.playerPlugin.playerTaskX.dataManager.dao;

import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import com.playerPlugin.playerTaskX.dataManager.impl.SQLiteManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 玩家任务进度DAO
 *
 * @author yvmoux
 * &#064;date  2025/11/03
 */
public class PlayerTaskProgressDAO {
    private final StorgeManager db;

    public PlayerTaskProgressDAO(StorgeManager db) {
        this.db = db;
    }

    public void initializeTaskProgress(String uuid, String taskId, int targetCount) {
        String sql = "INSERT OR IGNORE INTO player_task_progress (player_uuid, task_id, target_index, current_amount) VALUES (?, ?, ?, 0)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < targetCount; i++) {
                ps.setString(1, uuid);
                ps.setString(2, taskId);
                ps.setInt(3, i);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void increaseProgress(String uuid, String taskId, int targetIndex, int amount) {
        String sql = "UPDATE player_task_progress SET current_amount = current_amount + ? WHERE player_uuid = ? AND task_id = ? AND target_index = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, amount);
            ps.setString(2, uuid);
            ps.setString(3, taskId);
            ps.setInt(4, targetIndex);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public Map<Integer, Integer> getProgress(String uuid, String taskId) {
        Map<Integer, Integer> progress = new HashMap<>();
        String sql = "SELECT target_index, current_amount FROM player_task_progress WHERE player_uuid = ? AND task_id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, taskId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                progress.put(rs.getInt("target_index"), rs.getInt("current_amount"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return progress;
    }
}
