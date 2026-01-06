package com.playerPlugin.playerTaskX.api.utils;

import cn.yvmou.ylib.api.logger.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StorageUtil {
    private static Logger log = null;

    public StorageUtil(Logger log) {
        StorageUtil.log = log;
    }

    public static Path getDir(@NotNull JavaPlugin plugin, @NotNull String dir) {
        // Such as C:\Minecraft\Server\plugins\playerTaskX\tasks\
        Path p = plugin.getDataFolder().toPath().resolve(dir);

        if (Files.notExists(p)) {
            try {
                Files.createDirectories(p);
                if (log != null) log.info("Directory created: {}", dir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create directory: " + dir, e);
            }
        }
        return p;
    }

    public static Path getProgressDir(@NotNull JavaPlugin plugin, @NotNull String dir) {
        return getDir(plugin, "progress").resolve(dir);
    }

    /**
     * Get all file names with specified suffix in the directory (Supports Path, Recommended)
     * @param dir Target directory Path
     * @param fileSuffix File suffix (e.g., ".json")
     * @param includeSubDirs Whether to traverse subdirectories
     * @return List of file names matching the condition
     */
    public static List<String> getFileNamesBySuffix(Path dir, String fileSuffix, boolean includeSubDirs) {
        List<String> fileNameList = new ArrayList<>();

        // Check if directory exists and is a directory
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            if (log != null) log.error("Directory does not exist or is not a directory: {}", dir.toAbsolutePath());
            return fileNameList;
        }

        // Traverse directory
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(dir)) {
            for (Path path : directoryStream) {
                if (Files.isDirectory(path) && includeSubDirs) {
                    fileNameList.addAll(getFileNamesBySuffix(path, fileSuffix, includeSubDirs));
                } else if (Files.isRegularFile(path)) {
                    String fileName = path.getFileName().toString();
                    if (fileName.toLowerCase().endsWith(fileSuffix.toLowerCase())) {
                        fileNameList.add(fileName);
                    }
                }
            }
        } catch (IOException e) {
            if (log != null) log.error("Error occurred while traversing directory: {}", dir.toAbsolutePath(), e);
        }

        return fileNameList;
    }
}
