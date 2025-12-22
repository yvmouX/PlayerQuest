package com.playerPlugin.playerTaskX.storage;

import cn.yvmou.ylib.api.services.LoggerService;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UUUUUU {
    private static LoggerService log = null;

    public UUUUUU(LoggerService log) {
        UUUUUU.log = log;
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
}
