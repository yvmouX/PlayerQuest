package com.playerPlugin.playerTaskX.configuration;

import com.google.common.base.Charsets;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;

public class StorgeConfiguration {
    private final PlayerTaskX plugin;
    private File configFile = null;
    private FileConfiguration newConfig = null;

    public StorgeConfiguration(PlayerTaskX plugin) {
        this.plugin = plugin;
        configFile = new File(plugin.getDataFolder(), "storge.yml");
    }

    /**
     * 保存默认任务配置
     * <p>
     *     不会覆盖原有配置内容
     * </p>
     */
    public void saveDefaultConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("storge.yml", false);
        }
    }

    /**
     * 保存任务配置
     * <p>
     *     会覆盖原有配置内容
     * </p>
     */
    public void saveConfig() {
        try {
            getConfig().save(configFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save task config" + configFile, ex);
        }
    }


    /**
     * 获取任务配置
     * <p>
     *     获取
     * </p>
     * @return {@link FileConfiguration }
     */
    public FileConfiguration getConfig() {
        if  (newConfig == null) {
            reloadConfig();
        }

        return newConfig;
    }

    /**
     * 重新加载任务配置
     * <p>
     *     重载
     * </p>
     */
    public void reloadConfig() {
        newConfig = YamlConfiguration.loadConfiguration(configFile);

        final InputStream defConfigStream = plugin.getResource("storge.yml");
        if (defConfigStream == null) {
            return;
        }

        newConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, Charsets.UTF_8)));
    }
}
