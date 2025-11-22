package com.playerPlugin.bukkit;

import cn.yvmou.ylib.tools.LoggerTools;
import com.playerPlugin.common.Enum.PTXActionType;
import com.playerPlugin.common.Enum.PTXTaskType;
import com.playerPlugin.core.domain.Task.TaskDefinition;
import com.playerPlugin.core.domain.Task.TaskTarget;
import com.playerPlugin.core.domain.Task.TaskTrigger;
import com.playerPlugin.bukkit.configs.TaskConfig;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReadConfigToCache {
    private final LoggerTools log;

    public ReadConfigToCache(LoggerTools log) {
        this.log = log;
    }

    public void loadTasksToCache(TaskConfig taskConfig) {
        taskDefinitions.clear();

        TaskDefinition task = new TaskDefinition(null, null, null, new ArrayList<>(), null);

        FileConfiguration config = taskConfig.getTasksConfig();

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
            taskDefinitions.add(task);
        }
    }
}
