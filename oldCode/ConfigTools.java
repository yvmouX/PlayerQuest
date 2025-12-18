package cn.yvmou.ylib.tools;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.LocalDateTime;

public class ConfigTools {
    private final Plugin plugin;
    private final LoggerTools logger;

    public ConfigTools(Plugin plugin, LoggerTools logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public void checkConfigVersion() {
        String pluginVersion = plugin.getDescription().getVersion();
        String configVersion = plugin.getConfig().getString("version", "none");

        if (!configVersion.equals(pluginVersion)) {
            // 备份旧配置
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            File backupFile = new File(plugin.getDataFolder(), "config_old_v" + configVersion + ".yml");
            if (configFile.exists()) {
                configFile.renameTo(backupFile);
            }

            // 重新加载默认配置
            plugin.saveDefaultConfig();

            // 合并用户自定义的配置
            YamlConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);
            YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(backupFile);

            for (String key : oldConfig.getKeys(true)) {
                // 优先保留用户自定义（旧配置）内容
                newConfig.set(key, oldConfig.get(key));
            }

            // 更新版本号
            newConfig.set("version", pluginVersion);
            try {
                newConfig.save(configFile);
            } catch (Exception e) {
                logger.error("保存新配置文件时出错: " + e.getMessage());
            }

            // 5. 重新加载配置到内存
            plugin.reloadConfig();

            logger.info(ChatColor.GREEN + "配置文件已更新至 v" + pluginVersion + "，旧配置已备份。");
        }
    }
}