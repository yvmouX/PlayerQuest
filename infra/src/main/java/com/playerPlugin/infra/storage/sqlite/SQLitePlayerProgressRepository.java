package com.playerPlugin.infra.storage.sqlite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.core.domain.Task.TaskProgress;
import com.playerPlugin.core.repository.PlayerProgressRepository;
import com.playerPlugin.core.utils.DomainMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * - 使用连接池（HikariCP），配置合适的最大连接数；
 * - 对 `loadByType` 需要高效查询，建议在 `tasks` 表加 `type` 索引并缓存热点任务到内存（如热门活动任务）；
 * - 批量更新进度时可使用批量语句减少事务开销。
 */
public class SQLitePlayerProgressRepository implements PlayerProgressRepository {
    private final DataSource ds;
    private final ObjectMapper json;

    public SQLitePlayerProgressRepository(DataSource ds) {
        this.ds = ds;
        this.json = new ObjectMapper();
        this.json.findAndRegisterModules();
    }

    @Override
    public Optional<TaskProgress> find(UUID player, String taskId) {
        String sql = "SELECT progress_json, started_at, completed_at FROM player_progress WHERE player_uuid = ? AND task_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String pj = rs.getString(1);
                TaskProgress dto = json.readValue(pj, TaskProgress.class);
                return Optional.of(DomainMapper.progressFromDTO(dto));
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<TaskProgress> findByPlayer(UUID player) {
        return List.of();
    }

    @Override
    public void save(TaskProgress progress) {
        String pj;
        try {
            pj = json.writeValueAsString(DomainMapper.progressToDTO(progress));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        String sql = "INSERT INTO player_progress(player_uuid, task_id, progress_json, started_at, completed_at) VALUES (?, ?, ?, ?, ?)"
                + " ON CONFLICT(player_uuid, task_id) DO UPDATE SET progress_json = excluded.progress_json, started_at = excluded.started_at, completed_at = excluded.completed_at";
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, progress.getPlayerUuid().toString());
                ps.setString(2, progress.getTaskId());
                ps.setString(3, pj);
                ps.setLong(4, progress.getStartedAt().toEpochMilli());
                if (progress.getCompletedAt() != null) ps.setLong(5, progress.getCompletedAt().toEpochMilli());
                else ps.setNull(5, Types.BIGINT);
                ps.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(UUID player, String taskId) {

    }
}
