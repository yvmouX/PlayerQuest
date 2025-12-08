package com.playerPlugin.playerTaskX.storage.yaml;

import cn.yvmou.ylib.tools.LoggerTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.model.Task.TaskDefinition;
import com.playerPlugin.playerTaskX.model.Task.TaskProgress;
import com.playerPlugin.playerTaskX.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.utils.TimeUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        writeLock.lock();
        Path path = null;
        Path temp = null;
        try {
            path = dataDir.resolve(progress.getUuid() + ".yml");
            temp = dataDir.resolve(progress.getUuid() + ".yml");

            // 初始化文件
            JsonNode rootNode;
            if (!Files.exists(path) || Files.size(path) == 0) {
                Files.createFile(path);
                log.warn(String.format("玩家 %s 的任务进度仓库不存在，已自动创建", progress.getUuid()));
                rootNode = yamlMapper.createObjectNode(); // 空对象节点
            } else {
                // 读取 YAML 文件为 JsonNode 树模型
                rootNode = yamlMapper.readTree(path.toFile());
                // 兼容空文件/无效 YAML 避免解析失败
                if (rootNode == null || !rootNode.isObject()) {
                    rootNode = yamlMapper.createObjectNode();
                }
            }

            // 转换为可修改的ObjectNode
            ObjectNode mutNode = (ObjectNode) rootNode;
            // 增量更新所有字段
            if (progress.getUuid() != null) {
                mutNode.put("uuid", progress.getUuid().toString());
            }
            if (progress.getStatus() != null) {
                mutNode.put("status", progress.getStatus().toString());
            }
            if (progress.getTaskDefinition() != null) {
                mutNode.set("taskDefinition", yamlMapper.valueToTree(progress.getTaskDefinition()));
            }

            // 写入临时文件
            yamlMapper.writeValue(temp.toFile(), mutNode);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.debug(String.format("已成功保存玩家 %s 的任务",  progress.getUuid()));
        } catch (IOException e) {
            log.error(String.format("保存玩家 %s 的任务进度数据时发生错误: %s", progress.getUuid(), path), e);
            // 清理临时文件
            if (Files.exists(temp)) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e1) {
                    log.error(e1.getMessage(), e1);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void delete(UUID uuid, String taskId) {
        writeLock.lock();
        try {
            Files.deleteIfExists(dataDir.resolve(uuid + ".yml"));
        } catch (IOException e) {
            log.error(String.format("删除玩家 %s 的任务进度仓库时发生错误: ", uuid), e);
        } finally {
            writeLock.unlock();
        }
    }

    public void createForPlayer(Player player, TaskDefinition taskDefinition) {
        writeLock.lock();
        Path path = null;
        try {
            path = dataDir.resolve(player.getUniqueId() + ".yml");
            if (Files.exists(path) && Files.size(path) != 0) {
                log.warn(String.format("玩家 %s 的任务进度仓库已存在，不会重复创建", player.getName()));
                return;
            }

            TaskProgress taskProgress = new TaskProgress(player.getUniqueId(), taskDefinition, PTXTaskStatus.IN_PROGRESS, TimeUtil.getTime(), TimeUtil.getTime());
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
                log.debug(String.format("未加载玩家 %s 的任务进度，可能是由于其任务进度未被创建", player.getName()));
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
