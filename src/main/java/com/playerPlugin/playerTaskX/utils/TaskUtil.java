package com.playerPlugin.playerTaskX.utils;

import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.Task.Task;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.TaskRequireList;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.TaskTarget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskUtil {
    /**
     * 获取任务目标需要列表的部分映射
     *
     * <p>
     *     1. 如果任务不存在，返回空映射。
     *     2. 如果目标不存在，返回空映射。
     *     3. 如果任务目标需要列表不存在，返回空映射。
     *     4. 返回任务目标需要列表的部分映射。
     * </p>
     * <p>
     *     5. 如STONE_PICKAXE-1，返回 { "STONE_PICKAXE": "1" }
     * </p>
     *
     * @param taskID     任务 ID
     * @param targetID   目标 ID
     * @param playerTask 玩家任务
     * @return Map<String, String>
     */
    public static Map<String, String> getRequirePartMap(String taskID, String targetID, PlayerTask playerTask) {

        Task task = playerTask.getTask();
        if (task == null || !task.getId().equals(taskID)) {
            return new HashMap<>();
        }
        TaskTarget target = task.getTarget();
        if (target == null || !target.getTarget_id().contains(targetID)) {
            return new HashMap<>();
        }
        TaskRequireList requireList = target.getRequire().get(targetID);
        if (requireList == null) {
            return new HashMap<>();
        }

        List<String> requirePart1 = requireList.getRequireList().stream()
                .map(r -> r.split("-")[0])
                .toList();
        List<String> requirePart2 = requireList.getRequireList().stream()
                .map(r -> r.split("-")[1])
                .toList();

        Map<String, String> requirePartMap = new HashMap<>();

        for (int i = 0; i < requirePart1.size(); i++) {
            requirePartMap.put(requirePart1.get(i), requirePart2.get(i));
        }


        return requirePartMap;
    }
}
