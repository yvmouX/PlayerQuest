package com.playerPlugin.playerTaskX.storage.yaml;

import cn.yvmou.ylib.api.services.LoggerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.storage.TaskRepository;
import com.playerPlugin.playerTaskX.api.utils.StorageUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
    private final LoggerService log;

    public YamlTaskRepository(JavaPlugin plugin, LoggerService log) {
        this.log = log;
        this.dir = StorageUtil.getDir(plugin, "tasks");
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    @Override
    public List<TaskDefinition> loadAll() {
        readLock.lock();
        try {
            List<TaskDefinition> out = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.yml")) {
                boolean foundAny = false;
                for (Path taskFile : ds) {
                    foundAny = true;
                    String fileName = taskFile.getFileName().toString();
                    log.debug(String.format("正在加载任务文件：%s", fileName));

                    TaskDefinition taskDef = yamlMapper.readValue(taskFile.toFile(), TaskDefinition.class);

                    out.add(taskDef);
                }
                if (!foundAny) {
                    log.warn("没有从存储库加载到任何任务定义: yaml");
                }
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException(e);
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
            return Optional.empty();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void save(TaskDefinition def) {
        writeLock.lock();
        try {
            TaskDefinition taskDef = yamlMapper.convertValue(def, TaskDefinition.class);
            String yaml = yamlMapper.writeValueAsString(taskDef);
            Path taskPath = dir.resolve(taskDef.getId() + ".yml");

            Files.writeString(taskPath, yaml, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug(String.format("已保存任务 %s 到文件 %s", taskDef.getId(), taskPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
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