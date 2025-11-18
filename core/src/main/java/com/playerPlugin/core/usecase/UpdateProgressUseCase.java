package com.playerPlugin.core.usecase;

import com.playerPlugin.core.domain.PlayerTask.Task.TaskDefinition;
import com.playerPlugin.core.domain.PlayerTask.Task.TaskCondition;
import com.playerPlugin.core.event.EventBus;
import com.playerPlugin.core.extension.TaskType;
import com.playerPlugin.core.extension.TaskTypeRegistry;
import com.playerPlugin.core.repository.PlayerProgressRepository;
import com.playerPlugin.core.repository.TaskRepository;
import com.playerPlugin.core.utils.CoreEventUtil;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * - `CoreEventUtil` 是一个工具类，提供从通用 coreEvent 中提取 `player`/`type`/`extra` 的能力；
 * - `taskRepo.loadByType(eventType)` 是对 `loadAll()` 的优化，建议实现索引或缓存以避免每次全扫描。
 */
public class UpdateProgressUseCase {
    private final TaskRepository taskRepo;
    private final PlayerProgressRepository progressRepo;
    private final TaskTypeRegistry typeRegistry;
    private final EventBus eventBus;
    private final IssueRewardUseCase issueReward;

    public UpdateProgressUseCase(
            TaskRepository taskRepo,
            PlayerProgressRepository progressRepo,
            TaskTypeRegistry typeRegistry,
            EventBus eventBus,
            IssueRewardUseCase issueReward
    ) {
        this.taskRepo = taskRepo;
        this.progressRepo = progressRepo;
        this.typeRegistry = typeRegistry;
        this.eventBus = eventBus;
        this.issueReward = issueReward;
    }

    public void handleCoreEvent(Object coreEvent) {
        String eventType = CoreEventUtil.typeOf(coreEvent); // e.g. "kill"
        List<TaskDefinition> candidates = taskRepo.loadByType(eventType);
        if (candidates.isEmpty()) return;

        for (TaskDefinition taskDefinition : candidates) {
            boolean changed = false;
            UUID player = CoreEventUtil.playerOf(coreEvent);
            TaskProgress progress = progressRepo.find(player, def.getId()).orElseGet(() -> new TaskProgress(def.getId(), player));

            for (TaskCondition cond : def.getConditions()) {
                Optional<TaskType> tOpt = typeRegistry.get(def.getType());
                if (!tOpt.isPresent()) continue; // no type registered
                TaskType type = tOpt.get();
                if (!type.matches(cond, coreEvent)) continue;
                int delta = type.extractCount(cond, coreEvent);
                if (delta <= 0) continue;
                int prev = progress.getProgress(cond.getId());
                int next = Math.min(cond.getRequiredAmount(), prev + delta);
                if (next != prev) {
                    progress.setProgress(cond.getId(), next);
                    changed = true;
                }
            }

            if (changed) {
                if (progress.isComplete(def)) {
                    progress.setCompletedAt(Instant.now());
                    // 发放奖励（同步或异步依配置）
                    issueReward.execute(def, player);
                    eventBus.post(new PlayerTaskCompletedEvent(player, def.getId()));
                }
                progressRepo.save(progress);
                eventBus.post(new PlayerTaskProgressChangedEvent(player, def.getId(), progress));
            }
        }
    }
}