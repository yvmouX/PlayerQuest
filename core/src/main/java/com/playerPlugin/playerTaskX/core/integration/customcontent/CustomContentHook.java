package com.playerPlugin.playerTaskX.core.integration.customcontent;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** 一个「自定义内容」来源（ItemsAdder / CraftEngine）：回答「这个物品/方块是谁」；实现都走反射，没装的服连它的 API 类都不会加载。 */
public interface CustomContentHook {

    /** 软依赖名（同时也是探测用的插件名）。 */
    String plugin();

    /**
     * 目标配置里的前缀，形如 {@code itemsadder:}。
     * <p>
     * 主标识是原版材质/实体名，自定义 id 走 {@code ProgressContext.aliases}，
     * 因此写 {@code target: itemsadder:myitems:ruby_block} 与写原版材质名都能命中同一个方块。
     */
    String prefix();

    /** 该物品对应的自定义 id（{@code namespace:path}）；不是自定义物品时返回 {@code null}。 */
    @Nullable
    String itemId(ItemStack item);

    /** 该方块对应的自定义 id；不是自定义方块时返回 {@code null}。 */
    @Nullable
    String blockId(Block block);
}
