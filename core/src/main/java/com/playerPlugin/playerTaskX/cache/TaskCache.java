package com.playerPlugin.playerTaskX.cache;

import cn.yvmou.ylib.api.logger.Logger;
import cn.yvmou.ylib.api.scheduler.UniversalScheduler;
import cn.yvmou.ylib.api.scheduler.UniversalTask;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.api.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.api.storage.TaskRepository;
import com.playerPlugin.playerTaskX.storage.StorageFactory;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 缓存管理类
 * 1. 任务定义缓存 (TaskDefinition)
 * 2. 在线玩家任务进度缓存 (TaskProgress) - "在线玩家全量缓存" 模式
 */
public class TaskCache {

    private final Logger log;
    private final UniversalScheduler scheduler;
    private final TaskProgressRepository progressRepo;
    private final StorageFactory storageFactory;

    // 周期性保存任务句柄
    private UniversalTask autoSaveTask;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // ================== 缓存数据结构 ==================
    
    // 任务定义缓存: List<TaskDefinition>
    private final List<TaskDefinition> taskDefs;

    // 在线玩家进度缓存: UUID -> List<TaskProgress>
    // 使用 ConcurrentHashMap 保证线程安全
    private final ConcurrentMap<UUID, List<TaskProgress>> progressByUUID = new ConcurrentHashMap<>();


    public TaskCache(Logger log, UniversalScheduler scheduler, StorageFactory storageFactory) {
        this.log = log;
        this.scheduler = scheduler;
        this.storageFactory = storageFactory;

        // 从任务仓库获取所有已定义任务
        taskDefs = storageFactory.getRepository().loadAll();
        log.debug("已将 " + taskDefs.size() + " 个任务添加到缓存");
        // 获取进度仓库
        progressRepo = storageFactory.getProgressRepository();
        
        // 启动自动保存任务 (例如每5分钟)
        startAutoSave(6000L, 6000L); // 300秒 = 6000 ticks
    }

    // ================== 1. 任务定义 (TaskDefinition) 相关 ==================

    public void addTaskDef(TaskDefinition taskDefinition) {
        taskDefs.add(taskDefinition);
        log.debug("任务定义已添加到缓存：" + taskDefinition.getId());
    }
    
    public void removeTaskDef(TaskDefinition taskDefinition) {
        taskDefs.remove(taskDefinition);
        log.debug("任务定义已从缓存移除：" + taskDefinition.getId());
    }

    @Nullable
    public TaskDefinition getTaskDef(String id) {
        if (id == null || id.isBlank()) return null;
        for (TaskDefinition taskDef : taskDefs) {
            if (taskDef.getId().equals(id)) return taskDef;
        }
        return null;
    }

    public List<TaskDefinition> getTaskDefList() {
        return taskDefs;
    }

    // ================== 2. 玩家进度 (TaskProgress) 相关 ==================
    
    // ------ 核心操作：加载与卸载 ------

    /**
     * 玩家加入时调用：将数据加载到缓存
     */
    public void loadPlayer(UUID uuid, List<TaskProgress> data) {
        progressByUUID.put(uuid, new CopyOnWriteArrayList<>(data));
        log.debug("已加载玩家 " + uuid + " 的 " + data.size() + " 个任务进度到缓存");
    }

    /**
     * 玩家退出时调用：保存数据并移除缓存
     */
    public void unloadPlayer(UUID uuid) {
        List<TaskProgress> data = progressByUUID.remove(uuid);
        if (data != null) {
            // 异步保存，防止卡顿主线程
            scheduler.runAsync(() -> {
                try {
                    data.forEach(progressRepo::save);
                    log.debug("玩家 " + uuid + " 退出，已保存 " + data.size() + " 个任务进度");
                } catch (Exception e) {
                    log.error("保存退出玩家 " + uuid + " 数据失败", e);
                }
            });
        }
    }

    // ------ 读写操作 ------

    public void progressToCache(TaskProgress taskProgress) {
        UUID uuid = taskProgress.getUuid();
        // 只有在线玩家（在缓存中）才更新
        List<TaskProgress> list = progressByUUID.get(uuid);
        if (list != null) {
            // 简单去重：如果 list 中已存在该任务（根据 equals），则先移除旧的
            // 注意：TaskProgress 需要正确实现 equals/hashCode
            list.removeIf(tp -> tp.getTaskDefinition().getId().equals(taskProgress.getTaskDefinition().getId()));
            list.add(taskProgress);
            log.debug("更新玩家 " + uuid + " 缓存中的任务进度: " + taskProgress.getTaskDefinition().getId());
        } else {
             // 如果玩家不在线（不在缓存中），直接忽略或打个警告
             // 或者根据需求决定是否要临时加载（建议不要，保持简单）
             log.debug("尝试更新不在线玩家 " + uuid + " 的缓存，已忽略");
        }
    }
    
    public void removeProgressFromCache(UUID uuid, String taskId) {
        List<TaskProgress> list = progressByUUID.get(uuid);
        if (list != null) {
            list.removeIf(tp -> tp.getTaskDefinition().getId().equals(taskId));
        }
    }

    @Nullable
    public List<TaskProgress> getProgressList(UUID uuid) {
        return progressByUUID.get(uuid);
    }

    @Nullable
    public List<TaskProgress> getProgressList(UUID uuid, PTXTaskStatus status) {
        List<TaskProgress> list = progressByUUID.get(uuid);
        if (list == null) return Collections.emptyList();
        
        return list.stream()
                .filter(taskProgress -> taskProgress.getStatus() == status)
                .toList();
    }
    
    // ================== 自动保存逻辑 ==================

    private void startAutoSave(long initialDelayTicks, long periodTicks) {
        if (running.getAndSet(true)) return;

        autoSaveTask = scheduler.runTimerAsync(() -> {
            try {
                log.debug("开始自动保存所有在线玩家数据...");
                // 遍历所有在线玩家的数据进行保存
                // 使用 entrySet 遍历是线程安全的
                int count = 0;
                for (List<TaskProgress> list : progressByUUID.values()) {
                    for (TaskProgress tp : list) {
                        progressRepo.save(tp);
                        count++;
                    }
                }
                if (count > 0) {
                    log.debug("自动保存完成，共保存 " + count + " 条进度数据");
                }
            } catch (Exception e) {
                log.error("自动保存任务发生异常", e);
            }
        }, initialDelayTicks, periodTicks);
        
        log.info("自动保存任务已启动，间隔: " + periodTicks + " ticks");
    }
    
    public void shutdown() {
        if (!running.getAndSet(false)) return;
        
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        
        // 插件卸载时，同步保存所有数据
        log.info("正在保存所有缓存数据...");
        for (List<TaskProgress> list : progressByUUID.values()) {
            for (TaskProgress tp : list) {
                try {
                    progressRepo.save(tp);
                } catch (Exception e) {
                    log.error("保存失败", e);
                }
            }
        }
        progressByUUID.clear();
        log.info("所有数据已保存并清空缓存");
    }
}
