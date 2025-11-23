package com.playerPlugin.playerTaskX.storage.yaml;

import cn.yvmou.ylib.tools.LoggerTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.domain.Task.TaskProgress;
import com.playerPlugin.playerTaskX.service.TaskProgressRepository;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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


    /**
     * 加载指定玩家的任务进度
     *
     * @param player 玩家
     * @return {@link Optional }<{@link TaskProgress }>
     */
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
