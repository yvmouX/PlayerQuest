package com.playerPlugin.playerTaskX.api.model.condition;

import org.bukkit.entity.Player;

public class PermissionCondition implements Condition {
    private final String permission;

    public PermissionCondition(String permission) {
        if (permission == null || permission.isBlank()) {
            throw new IllegalArgumentException("permission cannot be null or blank");
        }
        this.permission = permission;
    }

    @Override
    public boolean isMet(Player player) {
        return player.hasPermission(permission);
    }
}
