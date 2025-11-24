package com.playerPlugin.playerTaskX.storage.yaml;

import cn.yvmou.ylib.tools.LoggerTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.domain.Task.TaskDefinition;
import com.playerPlugin.playerTaskX.domain.Task.TaskProgress;
import com.playerPlugin.playerTaskX.storage.TaskProgressRepository;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class YamlTaskProgressRepository implements TaskProgressRepository {
    private final Path dataDir;
    private final ObjectMapper yamlMapper;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    private final LoggerTools log;

    public YamlTaskProgressRepository(PlayerTaskX plugin, LoggerTools log){
        this.dataDir = getDataDir(plugin);
        this.log = log;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        // 确保任务目录存在
        if (Files.notExists(dataDir)) {
            try {
                Files.createDirectories(dataDir);
                log.info(String.format("data目录不存在，已自动创建：%s", dataDir));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Optional<TaskProgress> find(UUID player, String taskId) {
        return Optional.empty();
    }

    @Override
    public void save(TaskProgress progress) {

    }

    @Override
    public void delete(UUID player, String taskId) {

    }

    public void createForPlayer(Player player, TaskDefinition taskDefinition) {
        writeLock.lock();
        Path path = null;
        try {
            path = dataDir.resolve(player.getUniqueId() + ".yml");
            if (Files.exists(path)) {
                log.warn(String.format("玩家 %s 的任务进度文件已存在，不会重复创建", player.getName()));
                return;
            }

            TaskProgress taskProgress = new TaskProgress(player.getUniqueId(), taskDefinition);
            yamlMapper.writeValue(path.toFile(), taskProgress);
            log.debug(String.format("为玩家 %s 创建了任务进度文件 %s", player.getName(), path));
        } catch (IOException e) {
            log.error(String.format("为玩家 %s 创建任务进度文件时发生错误: %s", player.getName(), path), e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Optional<TaskProgress> loadForPlayer(Player player) {
        readLock.lock();
        Path progressFile = null;
        try {
            progressFile = dataDir.resolve(player.getUniqueId() + ".yml");

            if (!Files.exists(progressFile) || Files.size(progressFile) == 0) {
                log.warn(String.format("没有找到玩家 %s 的任务进度数据", player.getName()));
                return Optional.empty();
            }

            TaskProgress taskProgress = yamlMapper.readValue(progressFile.toFile(), TaskProgress.class);
            return Optional.ofNullable(taskProgress);

        } catch (IOException e) {
            log.error(String.format("加载玩家 %s 的任务进度数据时发生错误，文件可能已损坏: %s", player.getName(), progressFile), e);
            return Optional.empty();
        } finally {
            readLock.unlock();
        }
    }

    private Path getDataDir(JavaPlugin plugin) {
        // Such as C:\Minecraft\Server\plugins\playerTaskX\data\
        return plugin.getDataFolder().toPath().resolve("data");
    }
}
