package com.playerPlugin.core.usecase;

import com.playerPlugin.core.domain.Task.Reward;
import com.playerPlugin.core.domain.Task.TaskDefinition;
import com.playerPlugin.core.event.EventBus;

import java.util.List;
import java.util.UUID;

public class IssueRewardUseCase {
    private final List<RewardProvider> providers; // 注入所有可用的 RewardProvider（items, money, commands, custom）
    private final EventBus eventBus;

    public IssueRewardUseCase(List<RewardProvider> providers, EventBus bus) {
        this.providers = providers;
        this.eventBus = bus;
    }

    public void execute(TaskDefinition taskDefinition, UUID player) {
        for (Reward r : taskDefinition.getRewards()) {
            // find provider
            for (RewardProvider p : providers) {
                if (p.supports(r.getRewardType())) {
                    try {
                        p.issue(r, player);
                        eventBus.post(new RewardIssuedEvent(player, taskDefinition.getId(), r.getId()));
                    } catch (Throwable t) {
                        // log and optionally fallback
                    }
                    break;
                }
            }
        }
    }
}
