package com.playerPlugin.infra.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperPlugin.api.dto.TaskDefinitionDTO;
import com.playerPlugin.core.domain.PlayerTask.Task.TaskDefinition;
import com.playerPlugin.core.repository.TaskRepository;
import com.playerPlugin.core.utils.DomainMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * - 使用读写锁避免并发写入/读取冲突；
 * - 可选：为每个任务保存 checksum（hash）便于热重载时判断是否变更；
 * - 适用于小规模任务集（数百条任务）。大规模请使用 SQL 存储。
 */
public class YamlTaskRepository implements TaskRepository {
    private final Path tasksDir; // plugins/yourplugin/tasks/
    private final ObjectMapper yamlMapper; // Jackson 的 YAML 模块
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public YamlTaskRepository(Path tasksDir) {
        this.tasksDir = tasksDir;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.findAndRegisterModules();
        if (!Files.exists(tasksDir)) Files.createDirectories(tasksDir);
    }

    @Override
    public List<TaskDefinition> loadAll() {
        lock.readLock().lock();
        try {
            List<TaskDefinition> out = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(tasksDir, "*.yml")) {
                for (Path p : ds) {
                    String raw = Files.readString(p);
                    TaskDefinitionDTO dto = yamlMapper.readValue(raw, TaskDefinitionDTO.class);
                    out.add(DomainMapper.fromDTO(dto));
                }
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<TaskDefinition> findById(String id) {
        lock.readLock().lock();
        try {
            Path f = tasksDir.resolve(id + ".yml");
            if (!Files.exists(f)) return Optional.empty();
            String raw = Files.readString(f);
            TaskDefinitionDTO dto = yamlMapper.readValue(raw, TaskDefinitionDTO.class);
            return Optional.of(DomainMapper.fromDTO(dto));
        } catch (IOException e) {
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void save(TaskDefinition def) {
        lock.writeLock().lock();
        try {
            TaskDefinitionDTO dto = DomainMapper.toDTO(def);
            String yaml = yamlMapper.writeValueAsString(dto);
            Path f = tasksDir.resolve(def.getId() + ".yml");
            Files.writeString(f, yaml, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(String id) {
        lock.writeLock().lock();
        try {
            Files.deleteIfExists(tasksDir.resolve(id + ".yml"));
        } catch (IOException e) { /* log */ } finally {
            lock.writeLock().unlock();
        }
    }
}