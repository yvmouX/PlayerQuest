package com.playerPlugin.playerTaskX.core.integration;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 一个「自定义内容」来源（ItemsAdder / CraftEngine）：回答「这个物品/方块是谁」。
 *
 * <h2>为什么用一个接口管两家</h2>
 * 两家插件的能力几乎一样——都有自己的命名空间 id（{@code namespace:path}）、
 * 都能从 {@link ItemStack} / {@link Block} 反查 id、都能列出全部 id。
 * 差别只在「用哪套反射签名」，那是各实现类自己的事；监听器、校验、编辑器目录
 * 只需要这一份契约，因此加第三家（Oraxen / Nexo…）时不必再改它们。
 *
 * <h2>为什么都走反射</h2>
 * 两家都要么没有可直接依赖的公开仓库构件（ItemsAdder 的 API 在 JitPack 上，
 * CraftEngine 的构件与 Minecraft 版本绑定），要么会把一整棵依赖树拖进编译期。
 * 而本插件只用它们三四个方法，反射的代价更小：<b>没装的服务器连类都不会加载</b>，
 * 装了的服务器上方法名对不上时只损失这一个功能（记一条 warn）。
 */
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

    /** 全部已加载的自定义物品 id（编辑器选择器列出用）；取不到时返回空表。 */
    List<String> itemIds();

    /** 全部已加载的自定义方块 id；取不到时返回空表。 */
    List<String> blockIds();
}
