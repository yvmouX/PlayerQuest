package com.playerPlugin.playerTaskX.PlayerTask;

import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXActionType;
import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXTaskType;
import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.Task.Task;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.Requirement;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.TaskTarget;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTrigger;
import com.playerPlugin.playerTaskX.PlayerTask.Trigger.TaskTriggerExecutor;
import com.playerPlugin.playerTaskX.configs.TaskConfig;
import com.playerPlugin.playerTaskX.exceptions.InvalidTask;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.playerPlugin.playerTaskX.utils.Help.*;

public class TaskManager {
    private static volatile TaskManager instance;
    private final TaskConfig taskConfig;
    private final TaskProgressManger taskProgressManger;
    private final List<Task> tasks = new ArrayList<>(); // 存储定义的所有任务 (Task类)



    public TaskConfig getTaskConfig() {
        return taskConfig;
    }

    public TaskProgressManger getTaskProgressManger() {
        return taskProgressManger;
    }


    public TaskManager(TaskConfig taskConfig) {
        this.taskConfig = taskConfig;
        loadTasksToCache();
        taskProgressManger = new TaskProgressManger();
    }

    public static void init(TaskConfig taskConfig) {
        if (taskConfig == null) {
            throw new NullPointerException("TaskConfig 初始化失败，请检查tasks.yml");
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
     * 获取任务名称
     *
     * @param taskId 任务 ID
     * @return {@link String }
     */
    public String getTaskName(String taskId) {
        Task task = getTask(taskId);
        if (task == null) {
            return null;
        }
        return task.getName();
    }

    /**
     * 获取任务目标
     *
     * @param taskId 任务 ID
     * @return {@link List }<{@link TaskTarget }>
     */
    public List<TaskTarget> getTaskTargets(String taskId) {
        Task task = getTask(taskId);
        if (task == null) {
            return null;
        }
        return task.getTargets();
    }

    /**
     * 获取任务触发器
     *
     * @param taskId 任务 ID
     * @return {@link TaskTrigger }
     */
    public TaskTrigger getTaskTrigger(String taskId) {
        Task task = getTask(taskId);
        if (task == null) {
            return null;
        }
        return task.getTrigger();
    }

    /**
     * 获取任务类型
     *
     * @param taskId 任务 ID
     * @return {@link PTXTaskType }
     */
    public PTXTaskType getTaskType(String taskId) {
        Task task = getTask(taskId);
        if (task == null) {
            return null;
        }
        return task.getType();
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
    public void startTask(Player player, String taskId) throws SQLException {
        UUID uuid = player.getUniqueId();
        Task task = getTask(taskId);

        if (task == null) {
            player.sendMessage("§c任务 %s 不存在", taskId);
            return;
        };

        // 直接从 数据库 获取玩家进行中的任务列表
        // 检测是否已经有该任务。
        AtomicBoolean canStart = new AtomicBoolean(true);
        for (String id : sm.getTaskIdListFromDatabase(uuid, PTXTaskStatus.IN_PROGRESS)) {
            if (id.equals(taskId)) {
                player.sendMessage("§c你已经接受或者完成过任务: §e" + task.getName() + "！");
                canStart.set(false);
            }
        }

        // 向缓存添加任务
        if (canStart.get()) {
            sm.getCacheDAO().getPlayerTaskCache().updatePlayerTaskToCache(List.of(new PlayerTask(uuid, task)), true);
        }

        // 执行任务开始触发器
        TaskTriggerExecutor.execute(player, task.getTrigger().getOnTaskStart(), task);

        player.sendMessage("§a你已开始任务: §e" + task.getName());
    }

    /**
     * 关闭任务管理器
     * 确保所有数据保存到数据库
     */
    public void shutdown() {
        // 关闭缓存管理器，保存所有数据
        sm.getCacheDAO().getPlayerTaskCache().shutdown();
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

        Task task = new Task(null, null, null, new ArrayList<>(), null);

        FileConfiguration config = getTaskConfig().getTasksConfig();

        for (String taskId : config.getKeys(false)) {
            // taskID && taskName && taskType
            String taskName = config.getString(taskId + ".name");
            if (taskName == null) {
                log.error("加载任务: " + taskId + "失败。任务名称不能为空。");
                continue;
            }
            String taskType = config.getString(taskId + ".type");
            if (taskType == null) {
                log.error("加载任务: " + taskId + "失败。任务类型不能为空。");
                continue;
            }
            PTXTaskType taskTypeEnum = PTXTaskType.fromString(taskType);
            log.info("加载任务: " + taskId + " 任务类型: " + taskTypeEnum);
            if (taskTypeEnum == PTXTaskType.NONE) {
                log.error("加载任务: " + taskId + "失败。任务类型: " + taskType + " 无效。");
                continue;
            }

            task.setId(taskId);
            task.setType(taskTypeEnum);
            task.setName(taskName);

            // targets
            List<TaskTarget> targetsList = new ArrayList<>();
            List<Map<?, ?>> targetsMapList = config.getMapList(taskId + ".targets");
            log.info("加载任务: " + taskId + " 目标列表大小: " + targetsMapList.size() + " 路径: " + taskId + ".targets");
            log.trace("targetsMapList: " + targetsMapList);
            if (!targetsMapList.isEmpty()) {
                for (int idx = 0; idx < targetsMapList.size(); idx++) {
                    Map<?, ?> targetMap = targetsMapList.get(idx);
                    log.info("加载任务: " + taskId + " 目标索引: " + idx + " Action: " + targetMap.get("action"));

                    Object actionObj = targetMap.get("action");
                    if (actionObj == null) {
                        log.error("加载任务: " + taskId + " 目标索引: " + idx + " 操作类型不能为空。");
                        continue;
                    }
                    String actionType = actionObj.toString();
                    PTXActionType actionTypeEnum = PTXActionType.fromString(actionType);
                    if (actionTypeEnum == PTXActionType.NONE) {
                        log.error("加载任务: " + taskId + " 目标索引: " + idx + " 操作类型: " + actionType + " 无效。");
                        continue;
                    }

                    // require
                    Requirement requirement = null;
                    Object requiresObj = targetMap.get("require");
                    log.trace("requiresObj: " + requiresObj);
                    if (requiresObj instanceof Map<?, ?> reqMap) {
                        Object mObj = reqMap.get("material");
                        Object amountObj = reqMap.get("amount");
                        if (mObj == null || amountObj == null) {
                            log.error("加载任务: " + taskId + " 目标索引: " + idx + " 需求配置缺少 material 或 amount。");
                            continue;
                        }
                        String m = mObj.toString().toUpperCase(Locale.ENGLISH);
                        log.info("加载任务: " + taskId + " 目标索引: " + idx + " Material: " + m + " Amount: " + amountObj);
                        int amount;
                        try {
                            amount = Integer.parseInt(amountObj.toString());
                        } catch (NumberFormatException e) {
                            log.error("加载任务: " + taskId + " 目标索引: " + idx + " amount: " + amountObj + " 不是有效数字。");
                            continue;
                        }
                        if (m.isEmpty()) {
                            log.error("加载任务: " + taskId + " 目标索引: " + idx + " amount: " + amount + " 无效。");
                            continue;
                        }
                        Material material = Material.getMaterial(m);
                        if (material == null) {
                            log.error("加载任务: " + taskId + " 目标索引: " + idx + " Material: " + m + " 无效。");
                            continue;
                        }
                        requirement = new Requirement(material, amount);
                    }

                    // reward TODO



                    targetsList.add(new TaskTarget(actionTypeEnum, idx, requirement));
                }
            }

            task.setTargets(targetsList);

            // trigger
            ConfigurationSection triggerSec = config.getConfigurationSection(taskId + ".trigger");
            TaskTrigger trigger = new TaskTrigger();
            if (triggerSec != null) {
                trigger.setOnTaskStart(triggerSec.getStringList("on_task_start"));
                trigger.setOnTaskFinish(triggerSec.getStringList("on_task_finish"));
                trigger.setOnTaskFail(triggerSec.getStringList("on_task_fail"));
            }

            task.setTrigger(trigger);

            // 加入任务集合
            tasks.add(task);
        }
    }


}
