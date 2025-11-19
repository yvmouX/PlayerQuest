package com.playerPlugin.infra.storage.sqlite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperPlugin.api.model.TaskDefinitionDTO;
import com.playerPlugin.core.domain.Task.TaskDefinition;
import com.playerPlugin.core.repository.TaskRepository;
import com.playerPlugin.core.utils.DomainMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteTaskRepository implements TaskRepository {
    private final DataSource ds;
    private final ObjectMapper json;

    public SQLiteTaskRepository(DataSource ds) {
        this.ds = ds;
        this.json = new ObjectMapper();
        this.json.findAndRegisterModules();
    }

    @Override
    public List<TaskDefinition> loadAll() {
        String sql = "SELECT definition_json FROM definition_tasks";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            List<TaskDefinition> out = new ArrayList<>();
            while (rs.next()) {
                String j = rs.getString("definition_json");
                TaskDefinitionDTO dto = json.readValue(j, TaskDefinitionDTO.class);
                out.add(DomainMapper.fromDTO(dto));
            }
            return out;
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<TaskDefinition> findById(String id) {
        String sql = "SELECT definition_json FROM definition_tasks WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String j = rs.getString("definition_json");
                TaskDefinitionDTO dto = json.readValue(j, TaskDefinitionDTO.class);
                return Optional.ofNullable(DomainMapper.fromDTO(dto));
            }
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    @Override
    public void save(TaskDefinition taskDefinition) {
        String j;
        try {
            j = json.writeValueAsString(DomainMapper.toDTO(taskDefinition));
        }  catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        String sql = "INSERT OR REPLACE INTO definition_tasks (id, definition_json, updated_at) VALUES (?, ?, ?)";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, taskDefinition.getId());
            ps.setString(2, j);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void delete(String id) {
        String sql = "DELETE FROM definition_tasks WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
