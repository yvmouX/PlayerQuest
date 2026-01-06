package com.playerPlugin.playerTaskX.storage.yaml;

import cn.yvmou.ylib.api.logger.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.storage.TaskRepository;
import com.playerPlugin.playerTaskX.api.utils.StorageUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class YamlTaskRepository implements TaskRepository {
    private final Path dir;
    private final ObjectMapper yamlMapper;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    private final Logger log;

    public YamlTaskRepository(JavaPlugin plugin, Logger log) {
        this.log = log;
        this.dir = StorageUtil.getDir(plugin, "tasks");
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    @Override
    public List<TaskDefinition> loadAll() {
        readLock.lock();
        try {
            List<TaskDefinition> out = new ArrayList<>();
            // Ensure directory exists before trying to stream it
            if (!Files.exists(dir)) {
                log.warn("Task repository does not exist: {}", dir);
                return out;
            }

            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.yml")) {
                boolean foundAny = false;
                for (Path taskFile : ds) {
                    foundAny = true;
                    String fileName = taskFile.getFileName().toString();
                    log.debug("Loading task file: {}", fileName);

                    try {
                        TaskDefinition taskDef = yamlMapper.readValue(taskFile.toFile(), TaskDefinition.class);
                        out.add(taskDef);
                    } catch (Exception e) {
                        log.error("Failed to parse task file: {}", fileName, e);
                    }
                }
                if (!foundAny) {
                    log.warn("No task definitions loaded from repository: yaml");
                }
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load tasks from directory: " + dir, e);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Optional<TaskDefinition> findById(String id) {
        readLock.lock();
        try {
            Path taskPath = dir.resolve(id + ".yml");
            if (!Files.exists(taskPath)) return Optional.empty();

            TaskDefinition taskDef = yamlMapper.readValue(taskPath.toFile(), TaskDefinition.class);

            return Optional.of(taskDef);
        } catch (IOException e) {
            log.error("Failed to load task: {}", id, e);
            return Optional.empty();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void save(TaskDefinition def) {
        writeLock.lock();
        try {
            Path taskPath = dir.resolve(def.getId() + ".yml");
            Path tempPath = taskPath.resolveSibling(taskPath.getFileName() + ".tmp");

            // Write to temporary file first
            yamlMapper.writeValue(tempPath.toFile(), def);
            
            // Atomic move
            Files.move(tempPath, taskPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            
            log.debug("Saved task {} to file {}", def.getId(), taskPath);
        } catch (IOException e) {
            log.error("Failed to save task: {}", def.getId(), e);
            throw new RuntimeException("Failed to save task " + def.getId(), e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void delete(String id) {
        writeLock.lock();
        try {
            Files.deleteIfExists(dir.resolve(id + ".yml"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            writeLock.unlock();
        }
    }
}