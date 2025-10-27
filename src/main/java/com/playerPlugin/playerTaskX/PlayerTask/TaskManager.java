package com.playerPlugin.playerTaskX.PlayerTask;

import com.playerPlugin.playerTaskX.PlayerTask.Task.Task;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTrigger;
import com.playerPlugin.playerTaskX.PlayerTask.Trigger.TaskTriggerExecutor;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.configs.TaskConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

public class TaskManager {
    private static TaskManager instance;
    private final TaskConfig taskConfig;
    private final Map<String, Task> tasks = new HashMap<>(); // 存储定义的所有任务 (任务ID, Task类)
    private final Map<UUID, List<PlayerTask>> playerTasks = new HashMap<>(); // 存储

    public TaskManager(TaskConfig taskConfig) {
        instance = this;
        this.taskConfig = taskConfig;
        loadTasks();
    }

    public static TaskManager getInstance() {
        return instance;
    }

    /**
     * 从配置文件加载所有任务
     * <p>
     *     将所有于 tasks.yml 定义的任务存储在 @tasks
     * </p>
     */
    public void loadTasks() {
        tasks.clear();
        FileConfiguration config = taskConfig.getTasksConfig();
        Set<String> AllTaskId = config.getKeys(false);

        // 循环获取 tasks.yml 中的所有任务
        for (String taskId : AllTaskId) {
            ConfigurationSection taskSection = config.getConfigurationSection(taskId);
            if (taskSection == null) {
                PlayerTaskX.getYLib().getLoggerTools().error("加载任务: " + taskId + "失败。");
                continue;
            }

            // 加载任务名称、任务类型、任务条件
            String name = taskSection.getString("name", taskId);
            String type = taskSection.getString("type", "forever");
            String condition = taskSection.getString("condition", "none");

            // 加载目标
            ConfigurationSection targetSection = taskSection.getConfigurationSection("target");
            TaskTarget target = new TaskTarget();
            if (targetSection != null) {
                target.setTarget_id(targetSection.getString("target_id", ""));
                target.setAction(targetSection.getString("action", ""));
                target.setCount(targetSection.getInt("count", 1));
            }

            // 加载触发器
            ConfigurationSection triggerSection = taskSection.getConfigurationSection("trigger");
            TaskTrigger trigger = new TaskTrigger();
            if (triggerSection != null) {
                trigger.setOnTaskStart(triggerSection.getStringList("on_task_start"));
                trigger.setOnTaskFinish(triggerSection.getStringList("on_task_finish"));
                trigger.setOnTaskFail(triggerSection.getStringList("on_task_fail"));
            }

            Task task = new Task(taskId, name, type, target, condition, trigger);
            tasks.put(taskId, task);
        }
    }

    /**
     * 获取所有任务
     *
     * @return {@link Map }<{@link String }, {@link Task }>
     */
    public Map<String, Task> getTasks() {
        return tasks;
    }

    /**
     * 根据ID获取任务
     */
    public Task getTask(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * 玩家开始任务
     *
     * @param player 选手
     * @param taskId 任务 ID
     */
    public void startTask(Player player, String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) {
            player.sendMessage("§c任务开始失败\n" +
                    "这是一个不应出现的错误，请及时联系管理员！");
            return;
        };

        UUID uuid = player.getUniqueId();
        List<PlayerTask> playerTaskList = playerTasks.getOrDefault(uuid, new ArrayList<>());

        // 检查是否已经有该任务。包括已完成
        boolean hasTask = playerTaskList.stream()
                .anyMatch(pt -> pt.getTask().getId().equals(taskId) && 
                       pt.getStatus() != PlayerTaskStatus.COMPLETED);

        if (hasTask) {
            player.sendMessage("§c你已经接受或者完成过这个任务了！");
            return;
        }

        PlayerTask playerTask = new PlayerTask(uuid, task);
        playerTaskList.add(playerTask);
        playerTasks.put(uuid, playerTaskList);

        // 执行任务开始触发器 （pass） #TODO
        //TaskTriggerExecutor.execute(player, task.getTrigger().getOnTaskStart(), task);

        player.sendMessage("§a你已开始任务: §e" + task.getName());
    }

    /**
     * 获取玩家的任务列表
     *
     * @param uuid uuid
     * @return {@link List }<{@link PlayerTask }>
     */
    public List<PlayerTask> getPlayerTasks(UUID uuid) {
        return playerTasks.getOrDefault(uuid, new ArrayList<>());
    }

    /**
     * 获取玩家进行中的任务
     *
     * @param uuid uuid
     * @return {@link List }<{@link PlayerTask }>
     */
    public List<PlayerTask> getPlayerActiveTasks(UUID uuid) {
        return getPlayerTasks(uuid).stream()
                .filter(pt -> pt.getStatus() == PlayerTaskStatus.IN_PROGRESS)
                .toList();
    }

    /**
     * 检查并处理任务进度
     *
     * @param player     选手
     * @param actionType 作类型
     * @param itemType   项目类型
     */
    public void checkTaskProgress(Player player, String actionType, String itemType) {
        List<PlayerTask> activeTasks = getPlayerActiveTasks(player.getUniqueId());

        for (PlayerTask playerTask : activeTasks) {
            Task task = playerTask.getTask();
            TaskTarget target = task.getTarget();

            if (target == null) {
                player.sendMessage("§c无法获取目标%s，这是一个不应出现的异常。\n" +
                        "请及时联系管理员", target.toString());
                continue;
            };

            // 检查动作类型和目标物品是否匹配
            if (target.getAction().equalsIgnoreCase(actionType) &&
                    target.getTarget_id().equalsIgnoreCase(itemType)) {

                playerTask.addProgress(1);

                // 检查是否完成
                if (playerTask.isComplete()) {
                    completeTask(player, playerTask);
                }
            }
        }
    }

    /**
     * 完成任务
     *
     * @param player     选手
     * @param playerTask 玩家任务
     */
    private void completeTask(Player player, PlayerTask playerTask) {
        playerTask.setStatus(PlayerTaskStatus.COMPLETED);
        Task task = playerTask.getTask();

        // 执行任务完成触发器
        TaskTriggerExecutor.execute(player, task.getTrigger().getOnTaskFinish(), task);

        player.sendMessage("§a恭喜！你完成了任务: §e" + task.getName());
    }

    /**
     * 让任务失败
     *
     * @param player     选手
     * @param playerTask 玩家任务
     */
    public void failTask(Player player, PlayerTask playerTask) {
        playerTask.setStatus(PlayerTaskStatus.FAILED);
        Task task = playerTask.getTask();

        // 执行任务失败触发器
        TaskTriggerExecutor.execute(player, task.getTrigger().getOnTaskFail(), task);
    }

    /**
     * 重新加载任务
     */
    public void reload() {
        taskConfig.reloadTasksConfig();
        loadTasks();
    }
}
