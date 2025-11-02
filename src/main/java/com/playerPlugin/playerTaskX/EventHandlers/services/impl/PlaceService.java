package com.playerPlugin.playerTaskX.EventHandlers.services.impl;

import com.playerPlugin.playerTaskX.EventHandlers.services.EventCallback;
import com.playerPlugin.playerTaskX.PlayerTask.Enum.PlayerTaskStatus;
import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.Task.Task;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.TaskTarget;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.PlayerTask.Trigger.TaskTriggerExecutor;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PlaceService implements EventCallback<BlockPlaceEvent> {
    @Override
    public void onEvent(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String placedBlockType = event.getBlockPlaced().getType().name();

        // 处理玩家任务进度
        handlePlayerProgress(player, placedBlockType);
    }

    private void handlePlayerProgress(Player player, String placedBlockType) {
        // 获取玩家UUID
        var uuid = player.getUniqueId();
        
        // 获取玩家正在进行的任务
        List<PlayerTask> activeTasks = TaskManager.getInstance().getPlayerActiveTasks(uuid);
        
        // 如果没有活跃任务，直接返回
        if (activeTasks.isEmpty()) {
            return;
        }
        
        // 遍历所有活跃任务
        for (PlayerTask playerTask : activeTasks) {
            Task task = playerTask.getTask();
            TaskTarget target = task.getTarget();
            
            // 检查任务目标是否存在
            if (target == null || target.getTarget_id() == null || target.getAction() == null) {
                continue;
            }
            
            // 遍历任务目标ID
            for (String targetId : target.getTarget_id()) {
                // 获取该目标的动作类型
                var action = target.getAction().get(targetId);
                
                // 如果动作类型是PLACE（放置方块）且目标ID匹配放置的方块类型
                if (action != null && action.toString().equals("PLACE") && targetId.equalsIgnoreCase(placedBlockType)) {
                    // 获取任务要求
                    Set<String> requirementsList = target.getRequire().get(targetId).getRequireList();
                    // 任务需要的总进度
                    Map<String, Integer> progressMap = new HashMap<>();

                    if (requirementsList != null && !requirementsList.isEmpty()) {
                        // 对要求列表进行处理，提取方块类型部分
                        List<String> processedList = requirementsList.stream()
                                .map(e -> e.split("-")[0])
                                .toList();
                        // 提取进度部分
                        List<Integer> totalProgress = requirementsList.stream()
                                .map(e -> Integer.parseInt(e.split("-")[1]))
                                .toList();
                        // 构建进度映射
                        for (int i = 0; i < processedList.size(); i++) {
                            progressMap.put(processedList.get(i), totalProgress.get(i));
                        }

                        // 检查放置的方块是否符合要求
                        if (!processedList.contains(placedBlockType)) {
                            // 通知玩家放置的方块不符合要求
                            player.sendMessage("§c你放置的方块 §e" + placedBlockType + " §c不符合任务要求");
                            // 从进度映射中移除不符合要求的方块
                            progressMap.remove(placedBlockType);
                            continue;
                        }

                    }
                    
                    // 更新总进度（向后兼容）
                    playerTask.setProgress(playerTask.getProgress() + 1);
                    
                    // 更新特定项目的进度
                    playerTask.addItemProgress(placedBlockType, 1);
                    int currentItemProgress = playerTask.getItemProgress(placedBlockType);
                    
                    // 检查任务是否完成
                    Integer requiredProgress = progressMap.get(placedBlockType);
                    if (requiredProgress != null && currentItemProgress >= requiredProgress) {
                        // 更新任务状态为已完成
                        playerTask.setStatus(PlayerTaskStatus.COMPLETED);
                        
                        // 执行任务完成触发器
                        TaskTriggerExecutor.execute(player, task.getTrigger().getOnTaskFinish(), task);
                        
                        // 通知玩家任务完成
                        player.sendMessage("§a恭喜！你完成了任务: §e" + task.getName());
                        
                        // 更新数据库
                        StorgeManager.getInstance().updatePlayerTask(playerTask);
                    } else {
                        // 通知玩家任务进度
                        String requiredProgressStr = requiredProgress != null ? requiredProgress.toString() : "?";
                        player.sendMessage("§a任务 §e" + task.getName() + " §a进度: §e" + 
                                currentItemProgress + "§a/§e" + requiredProgressStr);
                        
                        // 更新数据库中的进度
                        StorgeManager.getInstance().updatePlayerTask(playerTask);
                    }
                }
            }
        }
    }
}
