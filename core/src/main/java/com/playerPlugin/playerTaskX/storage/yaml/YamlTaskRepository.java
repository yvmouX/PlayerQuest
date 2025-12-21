package com.playerPlugin.playerTaskX.storage.yaml;

import cn.yvmou.ylib.api.services.LoggerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.playerPlugin.playerTaskX.model.TaskDefinition;
import com.playerPlugin.playerTaskX.storage.TaskRepository;
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

/**
 * - 使用读写锁避免并发写入/读取冲突；
 * - 可选：为每个任务保存 checksum（hash）便于热重载时判断是否变更；
 * - 适用于小规模任务集（数百条任务）。大规模请使用 SQL 存储。
 */
public class YamlTaskRepository implements TaskRepository {
    private final Path tasksDir;
    private final ObjectMapper yamlMapper;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    private final LoggerService log;

    public YamlTaskRepository(JavaPlugin plugin, LoggerService log) {
        this.tasksDir = getTasksDir(plugin);
        this.log = log;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        // 确保任务目录存在
        if (Files.notExists(tasksDir)) {
            try {
                Files.createDirectories(tasksDir);
                log.info(String.format("tasks目录不存在，已自动创建：%s", tasksDir));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 从目录加载所有已定义任务，返回一个List<TaskDefinition>
     * <p>
     *     每个任务文件为一个 YAML 文件，文件名即任务 ID。
     *     每个任务文件的内容为一个 TaskDefinition 对象，序列化格式为 YAML。
     *     如果任务目录不存在，会自动创建。
     * </p>
     *
     * @return {@link List }<{@link TaskDefinition }>
     */
    @Override
    public List<TaskDefinition> loadAll() {
        readLock.lock();
        
        try {
            List<TaskDefinition> out = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(tasksDir, "*.yml")) {
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

    /**
     * 通过任务ID查找对应的已定义任务
     * <p>
     *     如果任务不存在，返回 Optional.empty()。
     * </p>
     *
     * @param id 任务ID
     * @return {@link Optional }<{@link TaskDefinition }>
     */
    @Override
    public Optional<TaskDefinition> findById(String id) {
        readLock.lock();
        try {
            Path taskPath = tasksDir.resolve(id + ".yml");
            if (!Files.exists(taskPath)) return Optional.empty();

            TaskDefinition taskDef = yamlMapper.readValue(taskPath.toFile(), TaskDefinition.class);

            return Optional.of(taskDef);
        } catch (IOException e) {
            return Optional.empty();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 保存已经定义任务到任务目录下
     *
     * @param def TaskDefinition 已定义任务
     */
    @Override
    public void save(TaskDefinition def) {
        writeLock.lock();
        try {
            TaskDefinition taskDef = yamlMapper.convertValue(def, TaskDefinition.class);
            String yaml = yamlMapper.writeValueAsString(taskDef);
            Path taskPath = tasksDir.resolve(taskDef.getId() + ".yml");

            Files.writeString(taskPath, yaml, StandardOpenOption.CREATE,  StandardOpenOption.TRUNCATE_EXISTING);
            log.debug(String.format("已保存任务 %s 到文件 %s", taskDef.getId(), taskPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 删除指定任务ID的任务文件
     *
     * @param id 任务ID
     */
    @Override
    public void delete(String id) {
        writeLock.lock();
        try {
            Files.deleteIfExists(tasksDir.resolve(id + ".yml"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            writeLock.unlock();
        }
    }

    private Path getTasksDir(JavaPlugin plugin) {
        // Such as C:\Minecraft\Server\plugins\playerTaskX\tasks\
        return plugin.getDataFolder().toPath().resolve("tasks");
    }
}