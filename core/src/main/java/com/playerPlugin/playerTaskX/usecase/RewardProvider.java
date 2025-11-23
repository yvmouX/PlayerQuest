package com.playerPlugin.playerTaskX.usecase;

import com.playerPlugin.playerTaskX.domain.Task.Reward;

import java.util.UUID;

public interface RewardProvider {
    boolean supports(String rewardType);

    void issue(Reward reward, UUID player);
}
