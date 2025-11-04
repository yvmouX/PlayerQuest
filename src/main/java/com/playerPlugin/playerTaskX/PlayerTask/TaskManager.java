package com.playerPlugin.playerTaskX.PlayerTask;

import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXActionType;
import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXTaskType;
import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.Task.Task;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.Requirement;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.TaskTarget;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTrigger;
import com.playerPlugin.playerTaskX.PlayerTask.Trigger.TaskTriggerExecutor;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.cache.PlayerTaskCache;
import com.playerPlugin.playerTaskX.configs.TaskConfig;
import com.playerPlugin.playerTaskX.exceptions.InvalidTask;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.*;

public class TaskManager {
    private static volatile TaskManager instance;
    private PlayerTaskCache playerTaskCache;
    private final TaskConfig taskConfig;
    private final List<Task> tasks = new ArrayList<>(); // 存储定义的所有任务 (Task类)


    /**
     * 获取玩家任务缓存
     *
     * @return {@link PlayerTaskCache }
     */
    public PlayerTaskCache getPlayerTaskCache() {
        return playerTaskCache;
    }


    public TaskManager(TaskConfig taskConfig) {
        if (instance != null) {
            throw new IllegalStateException("TaskManager 已经初始化");
        }
        this.taskConfig = taskConfig;
        PlayerTaskCache.init(PlayerTaskX.getInstance());
        this.playerTaskCache = PlayerTaskCache.getInstance();
        loadTasksToCache();
    }

    public static void init(TaskConfig taskConfig) {
        if (taskConfig == null) {
            throw new NullPointerException("TaskConfig 不能是 null");
        };
        if (instance == null) {
            synchronized (TaskManager.class) {
                if (instance == null) {
                    instance = new TaskManager(taskConfig);
                }
            }
        } else  {
            throw new IllegalStateException("TaskManager 已经初始化");
        }
    }

    public static TaskManager getInstance() {
        if (instance == null) {
            synchronized (TaskManager.class) {
                if (instance == null) {
                    throw new IllegalStateException("TaskManager 未初始化，请先调用init(TaskConfig taskConfig)");
                }
            }
        }
        return instance;
    }



    /**
     * 根据任务ID获取任务（Task.class）
     *
     * @param taskId 任务 ID
     * @return {@link Task }
     */
    public Task getTask(String taskId) {
        return tasks.stream().filter(task -> task.getId().equals(taskId)).findFirst().orElse(null);
    }

    /**
     * 获取所有任务（Task.class）
     *
     * @return {@link Collection }<{@link Task }>
     */
    public Collection<Task> getAllTasks() {
        return tasks.stream().toList();
    }

    /**
     * 开始任务
     *
     * @param player 选手
     * @param taskId 任务 ID
     */
    public void startTask(Player player, String taskId) {
        final PlayerTaskCache cache = getPlayerTaskCache();
        UUID uuid = player.getUniqueId();
        Task task = getTask(taskId);

        if (task == null) {
            player.sendMessage("§c任务开始失败,任务%s不存在", taskId);
            return;
        };

        // 获取玩家任务列表检查是否已经有该任务。包括已完成任务
        cache.getPlayerInProgressTaskIds(uuid).forEach(taskID -> {
            if (taskID.equals(task.getId())) {
                player.sendMessage("§c你已经接受或者完成过任务: §e" + task.getName() + "！");
            }
        });

        // 向缓存添加任务
        PlayerTaskCache.getInstance().updatePlayerTaskToCache(
                new PlayerTask(uuid, task),
                true
        );

        // 执行任务开始触发器
        TaskTriggerExecutor.execute(player, task.getTrigger().getOnTaskStart(), task);

        player.sendMessage("§a你已开始任务: §e" + task.getName());
    }



    /**
     * 从数据库加载玩家数据到缓存中
     *
     * @param playerTask 玩家任务
     */
    public void loadPlayerDataFromDatabase(PlayerTask playerTask) {
        PlayerTaskCache.getInstance().updatePlayerTaskToCache(playerTask, true);
    }

    /**
     * 关闭任务管理器
     * 确保所有数据保存到数据库
     */
    public void shutdown() {
        // 关闭缓存管理器，保存所有数据
        PlayerTaskCache.getInstance().shutdown();
    }

    /**
     * 检查 taskID 是否有效
     *
     * @param taskId 任务 ID
     * @return boolean
     * @throws InvalidTask 无效任务
     */
    public boolean isTaskIdValid(String taskId) {
        for (Task task : getAllTasks()) {
            if (task.getId().equals(taskId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将 taskID 转换为 PlayerTask
     *
     * @param taskId 任务 ID
     * @return {@link PlayerTask }
     */
    @Nullable
    @org.jetbrains.annotations.Nullable
    public PlayerTask toPlayerTask(UUID uuid, String taskId) throws InvalidTask {
        for (Task task : getAllTasks()) {
            if (task.getId().equals(taskId)) {
                return new PlayerTask(uuid, task);
            }
        }
        throw new InvalidTask("任务ID: " + taskId + " 无效。");
    }


    /**
     * 加载任务到内存中
     *
     */
    private void loadTasksToCache() {
        tasks.clear();

        FileConfiguration config = taskConfig.getTasksConfig();

        for (String taskId : config.getKeys(false)) {
            // taskID && taskName && taskType
            String taskName = config.getString(taskId + ".name");
            if (taskName == null) {
                PlayerTaskX.getYLib().getLoggerTools().error("加载任务: " + taskId + "失败。任务名称不能为空。");
                continue;
            }
            String taskType = config.getString(taskId + ".type");
            if (taskType == null) {
                PlayerTaskX.getYLib().getLoggerTools().error("加载任务: " + taskId + "失败。任务类型不能为空。");
                continue;
            }
            PTXTaskType taskTypeEnum = PTXTaskType.fromString(taskType);
            if (taskTypeEnum == PTXTaskType.NONE) {
                PlayerTaskX.getYLib().getLoggerTools().error("加载任务: " + taskId + "失败。任务类型: " + taskType + " 无效。");
                continue;
            }
            Task task = new Task();
            task.id = taskId;
            task.name = taskName;
            task.type = taskTypeEnum;

            // targets
            ConfigurationSection targets = config.getConfigurationSection(taskId + ".targets");
            if (targets != null) {
                for (String t : targets.getKeys(false)) {
                    ConfigurationSection tSec =  targets.getConfigurationSection(t);

                    if (tSec == null) {
                        PlayerTaskX.getYLib().getLoggerTools().error("加载任务: " + taskId + "目标索引: " + t + " 目标配置不能为空。");
                        continue;
                    }
                    String actionType = tSec.getString("action");
                    if (actionType == null) {
                        PlayerTaskX.getYLib().getLoggerTools().error("加载任务: " + taskId + "目标索引: " + t + " 操作类型不能为空。");
                        continue;
                    }
                    PTXActionType actionTypeEnum = PTXActionType.fromString(actionType);
                    if (actionTypeEnum == PTXActionType.NONE) {
                        PlayerTaskX.getYLib().getLoggerTools().error("加载任务: " + taskId + "目标索引: " + t + " 操作类型: " + actionType + " 无效。");
                        continue;
                    }

                    TaskTarget tt = new TaskTarget();
                    tt.action = actionTypeEnum;

                    List<Map<?, ?>> requireList =  tSec.getMapList("require");
                    for (Map<?, ?> reqMap : requireList) {
                        String m = reqMap.get("material").toString().toUpperCase(Locale.ENGLISH);
                        int amount = Integer.parseInt(reqMap.get("amount").toString());
                        if (m.isEmpty()) {
                            PlayerTaskX.getYLib().getLoggerTools().error("加载任务: " + taskId + "目标索引: " + t + " amount: " + amount + " 无效。");
                            continue;
                        }
                        Material material = Material.getMaterial(m);
                        if (material == null) {
                            PlayerTaskX.getYLib().getLoggerTools().error("加载任务: " + taskId + "目标索引: " + t + " Material: " + m + " 无效。");
                            continue;
                        }
                        tt.requires.add(new Requirement(material, amount));
                    }
                    task.targets.add(tt);
                }
            }
            tasks.add(task);

            // trigger
            ConfigurationSection triggerSec = config.getConfigurationSection("trigger");
            TaskTrigger trigger = new TaskTrigger();
            if (triggerSec != null) {
                trigger.setOnTaskStart(triggerSec.getStringList("on_task_start"));
                trigger.setOnTaskFinish(triggerSec.getStringList("on_task_finish"));
                trigger.setOnTaskFail(triggerSec.getStringList("on_task_fail"));
            }
        }
    }


}
