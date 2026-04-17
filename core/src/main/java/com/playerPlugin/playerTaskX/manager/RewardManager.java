package com.playerPlugin.playerTaskX.manager;

import com.playerPlugin.playerTaskX.api.model.reward.Reward;
import org.bukkit.entity.Player;

import java.util.List;

public class RewardManager {

    public void grantRewards(Player player, List<Reward> rewards) {
        for (Reward reward : rewards) {
            reward.grant(player);
        }
    }
}
