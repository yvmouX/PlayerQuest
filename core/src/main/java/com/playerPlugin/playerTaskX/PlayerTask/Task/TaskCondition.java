package com.playerPlugin.playerTaskX.PlayerTask.Task;

import org.bukkit.entity.Player;

import java.util.List;

interface TaskCondition {
    public boolean check(Player player);
}
class PlayerWhitelistCondition implements TaskCondition {
    List<String> whitelist;
    @Override
    public boolean check(Player p) {
        return whitelist.contains(p.getName());
    }
}