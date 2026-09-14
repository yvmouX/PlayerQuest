package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.core.integration.CustomContentHook;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 自定义内容来源的测试替身：只回答「有哪些 id」，不碰任何真实插件。 */
record FakeCustomContentHook(String plugin, String prefix, List<String> items, List<String> blocks)
        implements CustomContentHook {

    static FakeCustomContentHook of(String plugin, String prefix, List<String> items, List<String> blocks) {
        return new FakeCustomContentHook(plugin, prefix, items, blocks);
    }

    @Override
    public String itemId(ItemStack item) {
        return items.isEmpty() ? null : items.get(0);
    }

    @Override
    public String blockId(Block block) {
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    @Override
    public List<String> itemIds() {
        return items;
    }

    @Override
    public List<String> blockIds() {
        return blocks;
    }
}
