package com.playerPlugin.playerTaskX.api.model.objective;

import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;

public class BreakBlockObjective extends AbstractObjective {
    private final Material blockType;

    public BreakBlockObjective(String id, Material blockType, int amount) {
        super(id, amount);
        this.blockType = blockType;
    }

    @Override
    public boolean matchesEvent(Event event) {
        if (event instanceof BlockBreakEvent e) {
            return e.getBlock().getType() == blockType;
        }
        return false;
    }
}
