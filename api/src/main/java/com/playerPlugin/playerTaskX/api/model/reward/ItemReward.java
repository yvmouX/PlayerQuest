package com.playerPlugin.playerTaskX.api.model.reward;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ItemReward implements Reward {
    private final ItemStack item;

    public ItemReward(ItemStack item) {
        this.item = item.clone();
    }

    @Override
    public void grant(Player player) {
        player.getInventory().addItem(item);
    }
}
