//package com.playerPlugin.playerTaskX.废弃.configs;
//
//import com.google.common.base.Charsets;
//import com.playerPlugin.playerTaskX.PlayerTaskX;
//import org.bukkit.configuration.file.FileConfiguration;
//import org.bukkit.configuration.file.YamlConfiguration;
//
//import java.io.File;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.InputStreamReader;
//import java.util.logging.Level;
//
//public class TaskConfig {
//    private final PlayerTaskX plugin;
//    private File taskConfigFile = null;
//    private FileConfiguration newTaskConfig = null;
//
//    public TaskConfig(PlayerTaskX plugin) {
//        this.plugin = plugin;
//        taskConfigFile = new File(plugin.getDataFolder(), "tasks.yml");
//    }
//
//    /**
//     * 保存默认任务配置
//     * <p>
//     *     不会覆盖原有配置内容
//     * </p>
//     */
//    public void saveDefaultTaskConfig() {
//        if (!taskConfigFile.exists()) {
//            plugin.saveResource("tasks.yml", false);
//        }
//    }
//
//    /**
//     * 保存任务配置
//     * <p>
//     *     会覆盖原有配置内容
//     * </p>
//     */
//    public void saveTaskConfig() {
//        try {
//            getTasksConfig().save(taskConfigFile);
//        } catch (IOException ex) {
//            plugin.getLogger().log(Level.SEVERE, "Could not save task config" + taskConfigFile, ex);
//        }
//    }
//
//
//    /**
//     * 获取任务配置
//     * <p>
//     *     获取
//     * </p>
//     * @return {@link FileConfiguration }
//     */
//    public FileConfiguration getTasksConfig() {
//        if  (newTaskConfig == null) {
//            reloadTasksConfig();
//        }
//
//        return newTaskConfig;
//    }
//
//    /**
//     * 重新加载任务配置
//     * <p>
//     *     重载
//     * </p>
//     */
//    public void reloadTasksConfig() {
//        newTaskConfig = YamlConfiguration.loadConfiguration(taskConfigFile);
//
//        final InputStream defConfigStream = plugin.getResource("tasks.yml");
//        if (defConfigStream == null) {
//            return;
//        }
//
//        newTaskConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, Charsets.UTF_8)));
//    }
//}
