package com.playerPlugin.playerTaskX.api.model.reward;

import org.bukkit.entity.Player;

public class CommandReward implements Reward {
    private final String command;

    public CommandReward(String command) {
        this.command = command;
    }

    @Override
    public void grant(Player player) {
        String cmd = command.replace("{player}", player.getName());
        player.getServer().dispatchCommand(player.getServer().getConsoleSender(), cmd);
    }
}
