package com.playerPlugin.core.usecase;

import com.playerPlugin.core.domain.Task.Reward;

import java.util.UUID;

public interface RewardProvider {
    boolean supports(String rewardType);

    void issue(Reward reward, UUID player);
}
