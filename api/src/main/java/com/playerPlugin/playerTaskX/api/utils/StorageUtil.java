package com.playerPlugin.playerTaskX.api.utils;

import cn.yvmou.ylib.api.services.LoggerService;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StorageUtil {
    private static LoggerService log = null;

    public StorageUtil(LoggerService log) {
        StorageUtil.log = log;
    }

    public static Path getDir(@NotNull JavaPlugin plugin, @NotNull String dir) {
        // Such as C:\Minecraft\Server\plugins\playerTaskX\tasks\
        Path p = plugin.getDataFolder().toPath().resolve(dir);

        if (Files.notExists(p)) {
            try {
                Files.createDirectories(p);
                log.info("Directory created: {}", dir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return p;
    }

    public static Path getProgressDir(@NotNull JavaPlugin plugin, @NotNull String dir) {
        return getDir(plugin, "progress").resolve(dir);
    }

    /**
     * 获取指定目录下指定后缀的所有文件名（支持Path对象，推荐）
     * @param dir 目标目录的Path对象（替代原来的File对象）
     * @param fileSuffix 指定文件后缀（如".json"）
     * @param includeSubDirs 是否遍历子目录
     * @return 符合条件的文件名列表
     */
    public static List<String> getFileNamesBySuffix(Path dir, String fileSuffix, boolean includeSubDirs) {
        List<String> fileNameList = new ArrayList<>();

        // 检查目录是否存在且是目录（使用Java NIO的Files工具类）
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.error("目录不存在或不是目录：" + dir.toAbsolutePath());
            return fileNameList;
        }

        // 遍历目录下的所有路径（使用DirectoryStream遍历，效率更高）
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(dir)) {
            for (Path path : directoryStream) {
                if (Files.isDirectory(path) && includeSubDirs) {
                    // 如果是目录且需要遍历子目录，递归调用（传入Path对象）
                    fileNameList.addAll(getFileNamesBySuffix(path, fileSuffix, includeSubDirs));
                } else if (Files.isRegularFile(path)) {
                    // 如果是文件，判断后缀
                    String fileName = path.getFileName().toString();
                    // 忽略大小写匹配后缀
                    if (fileName.toLowerCase().endsWith(fileSuffix.toLowerCase())) {
                        fileNameList.add(fileName);
                    }
                }
            }
        } catch (IOException e) {
            // 捕获IO异常（比如目录访问权限不足）
            log.error("遍历目录时发生错误：" + dir.toAbsolutePath(), e);
        }

        return fileNameList;
    }
}
