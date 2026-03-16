package com.playerPlugin.playerTaskX.storage;

import cn.yvmou.ylib.api.logger.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.playerPlugin.playerTaskX.api.model.ObjectiveDefinition;
import com.playerPlugin.playerTaskX.api.storage.ObjectiveRepository;
import com.playerPlugin.playerTaskX.api.utils.StorageUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class YamlObjectiveRepository implements ObjectiveRepository {
    private final Path dir;
    private final ObjectMapper yamlMapper;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    private final Logger log;

    public YamlObjectiveRepository(JavaPlugin plugin, Logger log) {
        this.log = log;
        this.dir = StorageUtil.getDir(plugin, "objectives");
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    @Override
    public List<ObjectiveDefinition> loadAll() {
        readLock.lock();
        try {
            List<ObjectiveDefinition> out = new ArrayList<>();
            if (!Files.exists(dir)) {
                log.warn("Objective repository does not exist: {}", dir);
                return out;
            }

            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.yml")) {
                for (Path file : ds) {
                    try {
                        ObjectiveDefinition def = yamlMapper.readValue(file.toFile(), ObjectiveDefinition.class);
                        out.add(def);
                    } catch (Exception e) {
                        log.error("Failed to parse objective file: {}", file.getFileName(), e);
                    }
                }
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load objectives from directory: " + dir, e);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Optional<ObjectiveDefinition> findById(String id) {
        readLock.lock();
        try {
            Path path = dir.resolve(id + ".yml");
            if (!Files.exists(path)) return Optional.empty();

            return Optional.of(yamlMapper.readValue(path.toFile(), ObjectiveDefinition.class));
        } catch (IOException e) {
            log.error("Failed to load objective: {}", id, e);
            return Optional.empty();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void save(ObjectiveDefinition def) {
        writeLock.lock();
        try {
            Path path = dir.resolve(def.getId() + ".yml");
            Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");

            yamlMapper.writeValue(tempPath.toFile(), def);
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            
            log.debug("Saved objective {} to file {}", def.getId(), path);
        } catch (IOException e) {
            log.error("Failed to save objective: {}", def.getId(), e);
            throw new RuntimeException("Failed to save objective " + def.getId(), e);
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